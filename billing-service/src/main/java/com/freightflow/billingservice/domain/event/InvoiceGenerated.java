package com.freightflow.billingservice.domain.event;

import com.freightflow.billingservice.domain.model.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event emitted when an invoice is generated and issued.
 *
 * <p>Carries all data downstream consumers need (Event-Carried State Transfer).
 * Consumers: booking-service (update booking with invoice reference),
 * notification-service (send invoice email).</p>
 *
 * @param eventId     unique event identifier
 * @param invoiceId   the newly generated invoice
 * @param bookingId   the booking this invoice is for
 * @param customerId  the customer being invoiced
 * @param totalAmount the total invoice amount
 * @param dueDate     the payment due date
 * @param occurredAt  when the invoice was generated
 */
public record InvoiceGenerated(
        UUID eventId,
        UUID invoiceId,
        UUID bookingId,
        UUID customerId,
        Money totalAmount,
        LocalDate dueDate,
        Instant occurredAt
) implements BillingEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public InvoiceGenerated(UUID invoiceId, UUID bookingId, UUID customerId,
                            Money totalAmount, LocalDate dueDate, Instant occurredAt) {
        this(UUID.randomUUID(), invoiceId, bookingId, customerId, totalAmount, dueDate, occurredAt);
    }
}
