package com.freightflow.billingservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a payment received against an invoice.
 *
 * <p>Immutable record following DDD value object semantics. Each payment
 * records the amount, method, timestamp, and external reference.</p>
 *
 * @param paymentId the unique payment identifier
 * @param amount    the payment amount
 * @param method    the payment method used
 * @param paidAt    when the payment was received
 * @param reference the external payment reference (e.g., bank transaction ID)
 */
public record Payment(
        UUID paymentId,
        Money amount,
        PaymentMethod method,
        Instant paidAt,
        String reference
) {

    /**
     * Creates a validated Payment.
     */
    public Payment {
        Objects.requireNonNull(paymentId, "Payment ID must not be null");
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(method, "Payment method must not be null");
        Objects.requireNonNull(paidAt, "Paid-at timestamp must not be null");
        Objects.requireNonNull(reference, "Reference must not be null");
    }

    /**
     * The payment method used for the transaction.
     */
    public enum PaymentMethod {
        /** Bank transfer (ACH, SEPA, etc.). */
        BANK_TRANSFER,
        /** Credit card payment. */
        CREDIT_CARD,
        /** Wire transfer (SWIFT). */
        WIRE
    }
}
