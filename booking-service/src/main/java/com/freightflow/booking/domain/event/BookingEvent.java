package com.freightflow.booking.domain.event;

import com.freightflow.commons.domain.BookingId;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface for all booking domain events.
 *
 * <p>Using a sealed interface ensures that all event types are explicitly defined
 * and can be exhaustively matched in switch expressions (Java 21 pattern matching).
 * This enforces the Liskov Substitution Principle — any {@code BookingEvent} subtype
 * is a valid substitution wherever a {@code BookingEvent} is expected.</p>
 *
 * <p>Events are immutable records carrying all data needed by downstream consumers.
 * They follow the Event-Carried State Transfer pattern — consumers should NOT need
 * to call back to the booking service for additional data.</p>
 *
 * @see BookingCreated
 * @see BookingConfirmed
 * @see BookingCancelled
 */
public sealed interface BookingEvent
        permits BookingCreated, BookingConfirmed, BookingCancelled {

    /**
     * Unique identifier for this event instance.
     *
     * @return the event ID
     */
    UUID eventId();

    /**
     * The booking aggregate this event belongs to.
     *
     * @return the booking ID
     */
    BookingId bookingId();

    /**
     * When this event occurred.
     *
     * @return the event timestamp
     */
    Instant occurredAt();

    /**
     * The event type name used for serialization and routing.
     *
     * @return the fully qualified event type name
     */
    default String eventType() {
        return this.getClass().getSimpleName();
    }
}
