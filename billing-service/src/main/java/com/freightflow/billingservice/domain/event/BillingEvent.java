package com.freightflow.billingservice.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface for all billing domain events.
 *
 * <p>Using a sealed interface ensures that all event types are explicitly defined
 * and can be exhaustively matched in switch expressions (Java 21 pattern matching).
 * This enforces the Liskov Substitution Principle — any {@code BillingEvent} subtype
 * is a valid substitution wherever a {@code BillingEvent} is expected.</p>
 *
 * <p>Events are immutable records carrying all data needed by downstream consumers.
 * They follow the Event-Carried State Transfer pattern.</p>
 *
 * @see InvoiceGenerated
 * @see PaymentReceived
 * @see RefundIssued
 */
public sealed interface BillingEvent
        permits InvoiceGenerated, PaymentReceived, RefundIssued {

    /**
     * Unique identifier for this event instance.
     *
     * @return the event ID
     */
    UUID eventId();

    /**
     * The invoice this event belongs to.
     *
     * @return the invoice ID
     */
    UUID invoiceId();

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
