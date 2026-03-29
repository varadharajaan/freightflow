package com.freightflow.booking.application.saga;

import com.freightflow.booking.application.BookingService;
import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.infrastructure.adapter.out.external.VesselScheduleServiceClient;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Saga Orchestrator for the Booking Confirmation flow.
 *
 * <p>Coordinates a distributed transaction across multiple microservices using the
 * <b>Saga Orchestration</b> pattern. Each step is executed sequentially, and if
 * any step fails, compensating transactions are executed in reverse order to
 * maintain data consistency across services.</p>
 *
 * <h3>Saga Flow</h3>
 * <pre>
 * Step 1: Confirm Booking (booking-service)       → Compensate: Cancel Booking
 * Step 2: Reserve Vessel Capacity (vessel-service) → Compensate: Release Capacity
 * Step 3: Generate Invoice (billing-service)       → Compensate: Cancel Invoice
 * Step 4: Send Notification (notification-service)  → No compensation (fire-and-forget)
 * </pre>
 *
 * <h3>Failure Scenarios</h3>
 * <ul>
 *   <li>Failure at Step 1: No compensation needed (nothing to undo)</li>
 *   <li>Failure at Step 2: Cancel Booking</li>
 *   <li>Failure at Step 3: Release Capacity → Cancel Booking</li>
 *   <li>Failure at Step 4: Logged as warning (fire-and-forget), saga still completes</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * <p>Each saga execution is identified by a caller-provided idempotency key. If a saga
 * with the same key has already been executed, the existing result is returned without
 * re-executing the saga. This prevents duplicate operations when clients retry requests.</p>
 *
 * <h3>Transaction Boundaries</h3>
 * <p>The saga spans multiple services, so each step operates within its own transaction
 * boundary. The saga execution state is persisted after each step transition, providing
 * durability in case of process crashes.</p>
 *
 * @see SagaExecution
 * @see SagaStep
 * @see SagaStatus
 */
@Service
@Profiled(value = "bookingConfirmationSaga", slowThresholdMs = 5000)
public class BookingConfirmationSaga {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationSaga.class);

    private final BookingService bookingService;
    private final VesselScheduleServiceClient vesselClient;
    private final SagaExecutionRepository sagaRepository;

    /**
     * Creates a new {@code BookingConfirmationSaga} with all required dependencies.
     *
     * @param bookingService the booking application service for confirm/cancel operations
     * @param vesselClient   the vessel schedule client for capacity reservation/release
     * @param sagaRepository the repository for persisting saga execution state
     */
    public BookingConfirmationSaga(BookingService bookingService,
                                    VesselScheduleServiceClient vesselClient,
                                    SagaExecutionRepository sagaRepository) {
        this.bookingService = Objects.requireNonNull(bookingService, "BookingService must not be null");
        this.vesselClient = Objects.requireNonNull(vesselClient, "VesselScheduleServiceClient must not be null");
        this.sagaRepository = Objects.requireNonNull(sagaRepository, "SagaExecutionRepository must not be null");
    }

    /**
     * Executes the booking confirmation saga.
     *
     * <p>Orchestrates all four steps of the saga sequentially. If any step fails
     * (except the fire-and-forget notification), compensating transactions are
     * executed in reverse order to maintain data consistency.</p>
     *
     * <p>The saga is idempotent: if an execution with the same idempotency key
     * already exists, the existing result is returned without re-execution.</p>
     *
     * @param bookingId      the booking to confirm
     * @param voyageId       the voyage to assign to the booking
     * @param idempotencyKey the caller-provided idempotency key
     * @return the saga execution representing the final state
     */
    @Profiled(value = "executeSaga", slowThresholdMs = 3000)
    public SagaExecution execute(String bookingId, String voyageId, String idempotencyKey) {
        Objects.requireNonNull(bookingId, "Booking ID must not be null");
        Objects.requireNonNull(voyageId, "Voyage ID must not be null");
        Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");

        log.info("Saga starting: bookingId={}, voyageId={}, idempotencyKey={}",
                bookingId, voyageId, idempotencyKey);

        // ── Step 0: Idempotency check ──
        Optional<SagaExecution> existing = sagaRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Saga already exists for idempotencyKey={}, returning existing result: sagaId={}, status={}",
                    idempotencyKey, existing.get().getSagaId(), existing.get().getStatus());
            return existing.get();
        }

        // ── Create saga execution ──
        SagaExecution saga = SagaExecution.initiate(bookingId, voyageId, idempotencyKey);
        saga = sagaRepository.save(saga);

        log.info("Saga created: sagaId={}, bookingId={}, voyageId={}",
                saga.getSagaId(), bookingId, voyageId);

        // ── Step 1: Confirm Booking ──
        try {
            saga = executeConfirmBooking(saga, bookingId, voyageId);
        } catch (Exception e) {
            log.warn("Saga step CONFIRM_BOOKING failed: sagaId={}, bookingId={}, error={}",
                    saga.getSagaId(), bookingId, e.getMessage());
            saga.markFailed(SagaStep.CONFIRM_BOOKING, e.getMessage());
            sagaRepository.save(saga);
            log.info("Saga failed (no compensation needed — first step): sagaId={}", saga.getSagaId());
            return saga;
        }

        // ── Step 2: Reserve Vessel Capacity ──
        try {
            saga = executeReserveCapacity(saga, voyageId, bookingId);
        } catch (Exception e) {
            log.warn("Saga step RESERVE_CAPACITY failed: sagaId={}, voyageId={}, error={}",
                    saga.getSagaId(), voyageId, e.getMessage());
            saga.markFailed(SagaStep.RESERVE_CAPACITY, e.getMessage());
            sagaRepository.save(saga);
            compensate(saga, bookingId, voyageId);
            return saga;
        }

        // ── Step 3: Generate Invoice ──
        try {
            saga = executeGenerateInvoice(saga, bookingId);
        } catch (Exception e) {
            log.warn("Saga step GENERATE_INVOICE failed: sagaId={}, bookingId={}, error={}",
                    saga.getSagaId(), bookingId, e.getMessage());
            saga.markFailed(SagaStep.GENERATE_INVOICE, e.getMessage());
            sagaRepository.save(saga);
            compensate(saga, bookingId, voyageId);
            return saga;
        }

        // ── Step 4: Send Notification (fire-and-forget) ──
        try {
            saga = executeSendNotification(saga, bookingId);
        } catch (Exception e) {
            log.warn("Saga step SEND_NOTIFICATION failed (fire-and-forget, continuing): sagaId={}, bookingId={}, error={}",
                    saga.getSagaId(), bookingId, e.getMessage());
            // Fire-and-forget — no compensation, no failure. Saga still completes.
        }

        // ── Mark completed ──
        saga.markCompleted();
        saga = sagaRepository.save(saga);

        log.info("Saga completed successfully: sagaId={}, bookingId={}, completedSteps={}",
                saga.getSagaId(), bookingId, saga.getCompletedSteps().size());

        return saga;
    }

    // ==================== Step Execution Methods ====================

    /**
     * Step 1: Confirms the booking by transitioning it from DRAFT to CONFIRMED.
     */
    @Profiled(value = "sagaStep.confirmBooking", slowThresholdMs = 1000)
    private SagaExecution executeConfirmBooking(SagaExecution saga, String bookingId, String voyageId) {
        log.info("Saga step CONFIRM_BOOKING starting: sagaId={}, bookingId={}", saga.getSagaId(), bookingId);

        saga.advanceTo(SagaStep.CONFIRM_BOOKING);
        saga = sagaRepository.save(saga);

        Booking confirmed = bookingService.confirmBooking(bookingId, voyageId);

        log.info("Saga step CONFIRM_BOOKING completed: sagaId={}, bookingId={}, status={}",
                saga.getSagaId(), bookingId, confirmed.getStatus());

        return saga;
    }

    /**
     * Step 2: Reserves vessel capacity for the booking's TEU requirements.
     */
    @Profiled(value = "sagaStep.reserveCapacity", slowThresholdMs = 2000)
    private SagaExecution executeReserveCapacity(SagaExecution saga, String voyageId, String bookingId) {
        log.info("Saga step RESERVE_CAPACITY starting: sagaId={}, voyageId={}", saga.getSagaId(), voyageId);

        saga.advanceTo(SagaStep.RESERVE_CAPACITY);
        saga = sagaRepository.save(saga);

        Booking booking = bookingService.getBooking(bookingId);
        double requiredTeu = booking.getCargo().totalTeu();

        boolean reserved = vesselClient.checkVesselCapacity(voyageId, requiredTeu);
        if (!reserved) {
            throw new SagaStepException("Insufficient vessel capacity for voyageId=%s, requiredTeu=%.1f"
                    .formatted(voyageId, requiredTeu));
        }

        log.info("Saga step RESERVE_CAPACITY completed: sagaId={}, voyageId={}, teu={}",
                saga.getSagaId(), voyageId, requiredTeu);

        return saga;
    }

    /**
     * Step 3: Generates an invoice for the confirmed booking via the billing service.
     */
    @Profiled(value = "sagaStep.generateInvoice", slowThresholdMs = 2000)
    private SagaExecution executeGenerateInvoice(SagaExecution saga, String bookingId) {
        log.info("Saga step GENERATE_INVOICE starting: sagaId={}, bookingId={}", saga.getSagaId(), bookingId);

        saga.advanceTo(SagaStep.GENERATE_INVOICE);
        saga = sagaRepository.save(saga);

        // TODO: Replace with actual billing service call when billing-service is integrated.
        // For now, simulates a successful invoice generation.
        // billingServiceClient.generateInvoice(bookingId);

        log.info("Saga step GENERATE_INVOICE completed: sagaId={}, bookingId={}", saga.getSagaId(), bookingId);

        return saga;
    }

    /**
     * Step 4: Sends a booking confirmation notification (fire-and-forget).
     */
    @Profiled(value = "sagaStep.sendNotification", slowThresholdMs = 1000)
    private SagaExecution executeSendNotification(SagaExecution saga, String bookingId) {
        log.info("Saga step SEND_NOTIFICATION starting: sagaId={}, bookingId={}", saga.getSagaId(), bookingId);

        saga.advanceTo(SagaStep.SEND_NOTIFICATION);
        saga = sagaRepository.save(saga);

        // TODO: Replace with actual notification service call when notification-service is integrated.
        // notificationServiceClient.sendBookingConfirmation(bookingId);

        log.info("Saga step SEND_NOTIFICATION completed: sagaId={}, bookingId={}", saga.getSagaId(), bookingId);

        return saga;
    }

    // ==================== Compensation ====================

    /**
     * Compensates completed saga steps in reverse execution order.
     *
     * <p>Iterates through all completed compensatable steps and calls the appropriate
     * compensation method for each. If a compensation step itself fails, it is logged
     * as an ERROR but does not halt the compensation of remaining steps.</p>
     *
     * @param saga      the saga execution to compensate
     * @param bookingId the booking ID
     * @param voyageId  the voyage ID
     */
    private void compensate(SagaExecution saga, String bookingId, String voyageId) {
        List<SagaStep> stepsToCompensate = saga.stepsRequiringCompensation();

        if (stepsToCompensate.isEmpty()) {
            log.info("Saga compensation: no compensatable steps to roll back: sagaId={}", saga.getSagaId());
            return;
        }

        log.info("Saga compensation starting: sagaId={}, stepsToCompensate={}",
                saga.getSagaId(), stepsToCompensate);

        saga.startCompensation();
        sagaRepository.save(saga);

        for (SagaStep step : stepsToCompensate) {
            try {
                switch (step) {
                    case CONFIRM_BOOKING -> compensateConfirmBooking(saga, bookingId);
                    case RESERVE_CAPACITY -> compensateReserveCapacity(saga, voyageId, bookingId);
                    case GENERATE_INVOICE -> compensateGenerateInvoice(saga, bookingId);
                    case SEND_NOTIFICATION -> {
                        // No compensation needed for fire-and-forget
                    }
                }
            } catch (Exception e) {
                log.error("Saga compensation FAILED for step {}: sagaId={}, error={}. " +
                          "Manual intervention may be required.",
                        step, saga.getSagaId(), e.getMessage(), e);
            }
        }

        log.info("Saga compensation finished: sagaId={}, compensatedSteps={}",
                saga.getSagaId(), stepsToCompensate.size());
    }

    /**
     * Compensates Step 1: Cancels the booking that was confirmed during the saga.
     */
    private void compensateConfirmBooking(SagaExecution saga, String bookingId) {
        log.info("Compensating CONFIRM_BOOKING: sagaId={}, bookingId={}", saga.getSagaId(), bookingId);

        try {
            bookingService.cancelBooking(bookingId, "Saga compensation — rolling back booking confirmation");
            log.info("Compensation CONFIRM_BOOKING succeeded: sagaId={}, bookingId={}", saga.getSagaId(), bookingId);
        } catch (Exception e) {
            log.error("Compensation CONFIRM_BOOKING failed: sagaId={}, bookingId={}, error={}",
                    saga.getSagaId(), bookingId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Compensates Step 2: Releases the vessel capacity that was reserved during the saga.
     */
    private void compensateReserveCapacity(SagaExecution saga, String voyageId, String bookingId) {
        log.info("Compensating RESERVE_CAPACITY: sagaId={}, voyageId={}", saga.getSagaId(), voyageId);

        try {
            Booking booking = bookingService.getBooking(bookingId);
            double teu = booking.getCargo().totalTeu();
            boolean released = vesselClient.releaseCapacity(voyageId, teu);

            if (released) {
                log.info("Compensation RESERVE_CAPACITY succeeded: sagaId={}, voyageId={}, teu={}",
                        saga.getSagaId(), voyageId, teu);
            } else {
                log.error("Compensation RESERVE_CAPACITY returned false: sagaId={}, voyageId={}. " +
                          "Manual capacity reconciliation may be required.", saga.getSagaId(), voyageId);
            }
        } catch (Exception e) {
            log.error("Compensation RESERVE_CAPACITY failed: sagaId={}, voyageId={}, error={}",
                    saga.getSagaId(), voyageId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Compensates Step 3: Cancels the generated invoice.
     *
     * <p>Currently a placeholder — the billing service client is not yet integrated
     * directly. Invoice cancellation will be triggered via the BookingCancelled event
     * published when the booking is cancelled in the compensation of Step 1.</p>
     */
    private void compensateGenerateInvoice(SagaExecution saga, String bookingId) {
        log.info("Compensating GENERATE_INVOICE: sagaId={}, bookingId={}", saga.getSagaId(), bookingId);

        // TODO: Call billing service to cancel invoice directly when billing-service client is available.
        // For now, the invoice cancellation is handled implicitly via the BookingCancelled event
        // that billing-service consumes when the booking is cancelled in compensateConfirmBooking().

        log.info("Compensation GENERATE_INVOICE noted (handled via event): sagaId={}, bookingId={}",
                saga.getSagaId(), bookingId);
    }

    // ==================== Exception Type ====================

    /**
     * Exception thrown when a saga step fails due to a business rule violation
     * (as opposed to an infrastructure failure).
     */
    public static class SagaStepException extends RuntimeException {

        /**
         * Creates a new saga step exception with the given message.
         *
         * @param message the failure message
         */
        public SagaStepException(String message) {
            super(message);
        }

        /**
         * Creates a new saga step exception with the given message and cause.
         *
         * @param message the failure message
         * @param cause   the underlying cause
         */
        public SagaStepException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
