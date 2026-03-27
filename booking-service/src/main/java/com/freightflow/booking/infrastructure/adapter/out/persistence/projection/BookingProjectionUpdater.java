package com.freightflow.booking.infrastructure.adapter.out.persistence.projection;

import com.freightflow.booking.domain.event.BookingCancelled;
import com.freightflow.booking.domain.event.BookingConfirmed;
import com.freightflow.booking.domain.event.BookingCreated;
import com.freightflow.booking.domain.event.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Event listener that materializes booking domain events into the read model projection.
 *
 * <p>This component listens for {@link BookingEvent} instances published via Spring's
 * application event mechanism and updates the {@code booking_projections} table accordingly.
 * It is the bridge between the write side (event store) and the read side (projection table)
 * in the CQRS architecture.</p>
 *
 * <h3>Idempotency</h3>
 * <p>Each projection update checks the {@code last_event_version} before applying.
 * If the event version has already been applied (or a higher version exists), the update
 * is skipped. This makes the projector safe for at-least-once delivery semantics.</p>
 *
 * <h3>Event Handling</h3>
 * <p>Uses Java 21 pattern matching switch on the sealed {@link BookingEvent} hierarchy
 * for exhaustive, type-safe event dispatching:</p>
 * <ul>
 *   <li>{@link BookingCreated} → INSERT new projection row</li>
 *   <li>{@link BookingConfirmed} → UPDATE status and voyage ID</li>
 *   <li>{@link BookingCancelled} → UPDATE status and cancellation reason</li>
 * </ul>
 *
 * @see BookingEvent
 * @see BookingProjectionEntity
 * @see SpringDataProjectionRepository
 */
@Component
public class BookingProjectionUpdater {

    private static final Logger log = LoggerFactory.getLogger(BookingProjectionUpdater.class);

    private final SpringDataProjectionRepository projectionRepository;

    /**
     * Constructor injection of the projection repository.
     *
     * @param projectionRepository the Spring Data JPA repository for projection entities
     */
    public BookingProjectionUpdater(SpringDataProjectionRepository projectionRepository) {
        this.projectionRepository = Objects.requireNonNull(projectionRepository,
                "projectionRepository must not be null");
    }

    /**
     * Handles a booking domain event by updating the read model projection.
     *
     * <p>Dispatches to the appropriate handler using Java 21 pattern matching on the
     * sealed {@link BookingEvent} hierarchy. The compiler guarantees exhaustive matching.</p>
     *
     * @param event the domain event to project
     */
    @EventListener
    @Transactional
    public void onBookingEvent(BookingEvent event) {
        log.debug("Received booking event for projection: type={}, bookingId={}",
                event.eventType(), event.bookingId().asString());

        switch (event) {
            case BookingCreated created -> handleBookingCreated(created);
            case BookingConfirmed confirmed -> handleBookingConfirmed(confirmed);
            case BookingCancelled cancelled -> handleBookingCancelled(cancelled);
        }
    }

    /**
     * Handles a {@link BookingCreated} event by inserting a new projection row.
     *
     * <p>If a projection already exists for this booking (duplicate event), the insert
     * is skipped to maintain idempotency.</p>
     *
     * @param event the booking created event
     */
    private void handleBookingCreated(BookingCreated event) {
        String bookingIdStr = event.bookingId().asString();

        if (projectionRepository.existsById(event.bookingId().value())) {
            log.warn("Skipping duplicate BookingCreated projection: bookingId={}", bookingIdStr);
            return;
        }

        BookingProjectionEntity entity = new BookingProjectionEntity(
                event.bookingId().value(),
                event.customerId().value(),
                "DRAFT",
                event.origin().code(),
                event.destination().code(),
                event.containerType().name(),
                event.containerCount(),
                null,
                event.requestedDepartureDate(),
                1L,
                event.occurredAt()
        );

        projectionRepository.save(entity);

        log.info("Projected BookingCreated: bookingId={}, customerId={}, status=DRAFT",
                bookingIdStr, event.customerId().asString());
    }

    /**
     * Handles a {@link BookingConfirmed} event by updating the projection's status and voyage ID.
     *
     * <p>Idempotent: skips the update if the event version has already been applied.</p>
     *
     * @param event the booking confirmed event
     */
    private void handleBookingConfirmed(BookingConfirmed event) {
        String bookingIdStr = event.bookingId().asString();

        Optional<BookingProjectionEntity> existing =
                projectionRepository.findById(event.bookingId().value());

        if (existing.isEmpty()) {
            log.warn("Projection not found for BookingConfirmed — event arrived before creation? "
                    + "bookingId={}", bookingIdStr);
            return;
        }

        BookingProjectionEntity entity = existing.get();

        if (entity.getLastEventVersion() >= 2L) {
            log.warn("Skipping already-applied BookingConfirmed: bookingId={}, "
                    + "currentVersion={}", bookingIdStr, entity.getLastEventVersion());
            return;
        }

        entity.confirmBooking("CONFIRMED", event.voyageId().value(), 2L);
        projectionRepository.save(entity);

        log.info("Projected BookingConfirmed: bookingId={}, voyageId={}, status=CONFIRMED",
                bookingIdStr, event.voyageId().asString());
    }

    /**
     * Handles a {@link BookingCancelled} event by updating the projection's status and
     * cancellation reason.
     *
     * <p>Idempotent: skips the update if a cancellation has already been projected
     * (the version is already at or beyond the cancellation event).</p>
     *
     * @param event the booking cancelled event
     */
    private void handleBookingCancelled(BookingCancelled event) {
        String bookingIdStr = event.bookingId().asString();

        Optional<BookingProjectionEntity> existing =
                projectionRepository.findById(event.bookingId().value());

        if (existing.isEmpty()) {
            log.warn("Projection not found for BookingCancelled — event arrived before creation? "
                    + "bookingId={}", bookingIdStr);
            return;
        }

        BookingProjectionEntity entity = existing.get();
        long nextVersion = entity.getLastEventVersion() + 1;

        if ("CANCELLED".equals(entity.getStatus())) {
            log.warn("Skipping already-applied BookingCancelled: bookingId={}, "
                    + "currentStatus={}", bookingIdStr, entity.getStatus());
            return;
        }

        entity.cancelBooking("CANCELLED", event.reason(), nextVersion);
        projectionRepository.save(entity);

        log.info("Projected BookingCancelled: bookingId={}, reason='{}', status=CANCELLED",
                bookingIdStr, event.reason());
    }
}
