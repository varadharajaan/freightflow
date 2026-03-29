package com.freightflow.booking.application.saga;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a single execution of the Booking Confirmation Saga.
 *
 * <p>A {@code SagaExecution} tracks the full lifecycle of a distributed transaction
 * spanning multiple microservices. It records which steps have been completed, which
 * step (if any) failed, and the reason for failure.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #initiate(String, String, String)} — creates a new saga in {@link SagaStatus#STARTED}</li>
 *   <li>{@link #advanceTo(SagaStep)} — advances to the next step (records the step as completed)</li>
 *   <li>{@link #markCompleted()} — transitions to {@link SagaStatus#COMPLETED} on success</li>
 *   <li>{@link #markFailed(SagaStep, String)} — transitions to {@link SagaStatus#FAILED} on step failure</li>
 *   <li>{@link #startCompensation()} — transitions to {@link SagaStatus#COMPENSATING} during rollback</li>
 * </ol>
 *
 * <h3>Idempotency</h3>
 * <p>Every saga execution carries an idempotency key provided by the caller. If a saga
 * with the same idempotency key already exists, the orchestrator returns the existing
 * result instead of creating a duplicate execution.</p>
 *
 * <p>This is a domain entity — not a JPA entity. JPA persistence is handled by the
 * infrastructure adapter ({@code SagaExecutionJpaEntity}).</p>
 *
 * @see SagaStatus
 * @see SagaStep
 * @see BookingConfirmationSaga
 */
public class SagaExecution {

    private final UUID sagaId;
    private final String bookingId;
    private final String voyageId;
    private final String idempotencyKey;
    private final Instant startedAt;
    private final List<SagaStep> completedSteps;

    private SagaStatus status;
    private SagaStep currentStep;
    private SagaStep failedStep;
    private String failureReason;
    private Instant completedAt;

    /**
     * Private constructor — use {@link #initiate(String, String, String)} factory method
     * or {@link #reconstitute} for rehydration from persistence.
     */
    private SagaExecution(UUID sagaId, String bookingId, String voyageId,
                          String idempotencyKey, Instant startedAt) {
        this.sagaId = Objects.requireNonNull(sagaId, "Saga ID must not be null");
        this.bookingId = Objects.requireNonNull(bookingId, "Booking ID must not be null");
        this.voyageId = Objects.requireNonNull(voyageId, "Voyage ID must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "Started-at timestamp must not be null");
        this.completedSteps = new ArrayList<>();
        this.status = SagaStatus.STARTED;
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a new saga execution in {@link SagaStatus#STARTED} status.
     *
     * <p>The saga is assigned a unique ID and timestamped. No steps have been
     * executed yet — the orchestrator must call {@link #advanceTo(SagaStep)}
     * to begin execution.</p>
     *
     * @param bookingId      the booking being confirmed
     * @param voyageId       the voyage to assign to the booking
     * @param idempotencyKey the caller-provided idempotency key (must not be blank)
     * @return a new saga execution ready for step execution
     * @throws IllegalArgumentException if the idempotency key is blank
     */
    public static SagaExecution initiate(String bookingId, String voyageId, String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
        return new SagaExecution(UUID.randomUUID(), bookingId, voyageId, idempotencyKey, Instant.now());
    }

    /**
     * Reconstitutes a saga execution from persisted state.
     *
     * <p>Used only by the persistence adapter when loading from the database.
     * No validation is performed — the data has already been validated at creation time.</p>
     *
     * @param sagaId         the saga execution ID
     * @param bookingId      the booking ID
     * @param voyageId       the voyage ID
     * @param status         the current saga status
     * @param currentStep    the current (or last attempted) step
     * @param completedSteps the list of completed steps
     * @param failedStep     the step that failed (nullable)
     * @param failureReason  the failure reason (nullable)
     * @param idempotencyKey the idempotency key
     * @param startedAt      when the saga started
     * @param completedAt    when the saga completed (nullable)
     * @return a reconstituted saga execution
     */
    public static SagaExecution reconstitute(UUID sagaId, String bookingId, String voyageId,
                                              SagaStatus status, SagaStep currentStep,
                                              List<SagaStep> completedSteps, SagaStep failedStep,
                                              String failureReason, String idempotencyKey,
                                              Instant startedAt, Instant completedAt) {
        var saga = new SagaExecution(sagaId, bookingId, voyageId, idempotencyKey, startedAt);
        saga.status = status;
        saga.currentStep = currentStep;
        saga.completedSteps.addAll(completedSteps);
        saga.failedStep = failedStep;
        saga.failureReason = failureReason;
        saga.completedAt = completedAt;
        return saga;
    }

    // ==================== State Transitions ====================

    /**
     * Advances the saga to the specified step.
     *
     * <p>Sets the current step, updates the status to the corresponding step status,
     * and records the previous step as completed (if any).</p>
     *
     * @param step the step to advance to
     * @throws IllegalStateException if the saga is in a terminal state
     */
    public void advanceTo(SagaStep step) {
        Objects.requireNonNull(step, "Step must not be null");
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot advance saga %s — already in terminal state %s".formatted(sagaId, status));
        }

        // Record the previous step as completed (if we had one)
        if (currentStep != null && !completedSteps.contains(currentStep)) {
            completedSteps.add(currentStep);
        }

        this.currentStep = step;
        this.status = mapStepToStatus(step);
    }

    /**
     * Marks the saga as failed at the specified step.
     *
     * <p>Records the failed step, failure reason, and completion timestamp.
     * The saga transitions to {@link SagaStatus#FAILED}.</p>
     *
     * @param step   the step that failed
     * @param reason the failure reason
     * @throws IllegalStateException if the saga is already in a terminal state
     */
    public void markFailed(SagaStep step, String reason) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot mark saga %s as failed — already in terminal state %s".formatted(sagaId, status));
        }
        this.failedStep = step;
        this.failureReason = Objects.requireNonNull(reason, "Failure reason must not be null");
        this.status = SagaStatus.FAILED;
        this.completedAt = Instant.now();
    }

    /**
     * Marks the saga as successfully completed.
     *
     * <p>Records the last step as completed and sets the completion timestamp.
     * The saga transitions to {@link SagaStatus#COMPLETED}.</p>
     *
     * @throws IllegalStateException if the saga is already in a terminal state
     */
    public void markCompleted() {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot mark saga %s as completed — already in terminal state %s".formatted(sagaId, status));
        }

        // Record the current step as completed
        if (currentStep != null && !completedSteps.contains(currentStep)) {
            completedSteps.add(currentStep);
        }

        this.status = SagaStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Transitions the saga to the compensation phase.
     *
     * <p>Called before the orchestrator begins rolling back completed steps.
     * The saga transitions to {@link SagaStatus#COMPENSATING}.</p>
     *
     * @throws IllegalStateException if the saga is already in a terminal state
     */
    public void startCompensation() {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot start compensation for saga %s — already in terminal state %s".formatted(sagaId, status));
        }
        this.status = SagaStatus.COMPENSATING;
    }

    /**
     * Returns the completed steps that require compensation, sorted in reverse execution order.
     *
     * <p>Only steps that are {@link SagaStep#isCompensatable()} are included.
     * Steps are returned in compensation order (highest execution order first).</p>
     *
     * @return an unmodifiable list of steps to compensate in reverse order
     */
    public List<SagaStep> stepsRequiringCompensation() {
        return completedSteps.stream()
                .filter(SagaStep::isCompensatable)
                .sorted(Comparator.comparingInt(SagaStep::compensationOrder))
                .toList();
    }

    // ==================== Accessors ====================

    /**
     * Returns the unique saga execution identifier.
     *
     * @return the saga ID
     */
    public UUID getSagaId() {
        return sagaId;
    }

    /**
     * Returns the booking being confirmed by this saga.
     *
     * @return the booking ID
     */
    public String getBookingId() {
        return bookingId;
    }

    /**
     * Returns the voyage assigned to the booking.
     *
     * @return the voyage ID
     */
    public String getVoyageId() {
        return voyageId;
    }

    /**
     * Returns the current saga status.
     *
     * @return the status
     */
    public SagaStatus getStatus() {
        return status;
    }

    /**
     * Returns the current (or last attempted) saga step.
     *
     * @return the current step, or {@code null} if no step has started
     */
    public SagaStep getCurrentStep() {
        return currentStep;
    }

    /**
     * Returns an unmodifiable view of the completed steps.
     *
     * @return the completed steps in execution order
     */
    public List<SagaStep> getCompletedSteps() {
        return Collections.unmodifiableList(completedSteps);
    }

    /**
     * Returns the step that caused the saga to fail.
     *
     * @return the failed step, or {@code null} if the saga has not failed
     */
    public SagaStep getFailedStep() {
        return failedStep;
    }

    /**
     * Returns the reason for saga failure.
     *
     * @return the failure reason, or {@code null} if the saga has not failed
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Returns the caller-provided idempotency key.
     *
     * @return the idempotency key
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Returns when the saga execution started.
     *
     * @return the start timestamp
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Returns when the saga execution completed (succeeded or failed).
     *
     * @return the completion timestamp, or {@code null} if still in progress
     */
    public Instant getCompletedAt() {
        return completedAt;
    }

    // ==================== Internal Helpers ====================

    private SagaStatus mapStepToStatus(SagaStep step) {
        return switch (step) {
            case CONFIRM_BOOKING -> SagaStatus.CONFIRMING_BOOKING;
            case RESERVE_CAPACITY -> SagaStatus.RESERVING_CAPACITY;
            case GENERATE_INVOICE -> SagaStatus.GENERATING_INVOICE;
            case SEND_NOTIFICATION -> SagaStatus.SENDING_NOTIFICATION;
        };
    }

    @Override
    public String toString() {
        return "SagaExecution[sagaId=%s, bookingId=%s, status=%s, currentStep=%s, completedSteps=%d]".formatted(
                sagaId, bookingId, status,
                currentStep != null ? currentStep.name() : "NONE",
                completedSteps.size());
    }
}
