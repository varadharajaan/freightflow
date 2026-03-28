package com.freightflow.billingservice.domain.event;

import com.freightflow.billingservice.domain.model.Money;
import com.freightflow.billingservice.domain.model.Payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a payment is received against an invoice.
 *
 * <p>Carries all data downstream consumers need (Event-Carried State Transfer).
 * Consumers: booking-service (mark as paid), notification-service (send receipt).</p>
 *
 * @param eventId    unique event identifier
 * @param invoiceId  the invoice that was paid
 * @param bookingId  the booking associated with the invoice
 * @param paymentId  the payment identifier
 * @param amount     the payment amount
 * @param method     the payment method
 * @param occurredAt when the payment was received
 */
public record PaymentReceived(
        UUID eventId,
        UUID invoiceId,
        UUID bookingId,
        UUID paymentId,
        Money amount,
        Payment.PaymentMethod method,
        Instant occurredAt
) implements BillingEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public PaymentReceived(UUID invoiceId, UUID bookingId, UUID paymentId,
                           Money amount, Payment.PaymentMethod method, Instant occurredAt) {
        this(UUID.randomUUID(), invoiceId, bookingId, paymentId, amount, method, occurredAt);
    }
}
