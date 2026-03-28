package com.freightflow.billingservice.domain.event;

import com.freightflow.billingservice.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a refund is issued for a cancelled invoice.
 *
 * <p>Carries all data downstream consumers need (Event-Carried State Transfer).
 * Consumers: notification-service (send refund confirmation).</p>
 *
 * @param eventId     unique event identifier
 * @param invoiceId   the invoice that was cancelled and refunded
 * @param bookingId   the booking associated with the invoice
 * @param customerId  the customer receiving the refund
 * @param refundAmount the amount being refunded
 * @param reason      the cancellation reason
 * @param occurredAt  when the refund was issued
 */
public record RefundIssued(
        UUID eventId,
        UUID invoiceId,
        UUID bookingId,
        UUID customerId,
        Money refundAmount,
        String reason,
        Instant occurredAt
) implements BillingEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public RefundIssued(UUID invoiceId, UUID bookingId, UUID customerId,
                        Money refundAmount, String reason, Instant occurredAt) {
        this(UUID.randomUUID(), invoiceId, bookingId, customerId, refundAmount, reason, occurredAt);
    }
}
