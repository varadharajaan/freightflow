package com.freightflow.customerservice.domain.event;

import com.freightflow.commons.domain.CustomerId;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface for all customer domain events.
 *
 * <p>Using a sealed interface ensures that all event types are explicitly defined
 * and can be exhaustively matched in switch expressions (Java 21 pattern matching).
 * This enforces the Liskov Substitution Principle — any {@code CustomerEvent} subtype
 * is a valid substitution wherever a {@code CustomerEvent} is expected.</p>
 *
 * <p>Events are immutable records carrying all data needed by downstream consumers.
 * They follow the Event-Carried State Transfer pattern — consumers should NOT need
 * to call back to the customer service for additional data.</p>
 *
 * @see CustomerRegistered
 * @see CustomerSuspended
 * @see CreditAllocated
 * @see CreditReleased
 */
public sealed interface CustomerEvent
        permits CustomerRegistered, CustomerSuspended, CreditAllocated, CreditReleased {

    /**
     * Unique identifier for this event instance.
     *
     * @return the event ID
     */
    UUID eventId();

    /**
     * The customer aggregate this event belongs to.
     *
     * @return the customer ID
     */
    CustomerId customerId();

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
