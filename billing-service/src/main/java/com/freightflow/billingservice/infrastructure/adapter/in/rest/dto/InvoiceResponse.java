package com.freightflow.billingservice.infrastructure.adapter.in.rest.dto;

import com.freightflow.billingservice.domain.model.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * REST response DTO for invoice data.
 *
 * @param invoiceId       the invoice UUID
 * @param bookingId       the associated booking UUID
 * @param customerId      the customer UUID
 * @param status          the current invoice status
 * @param totalAmount     the total invoice amount
 * @param currency        the currency code
 * @param lineItemCount   the number of line items
 * @param dueDate         the payment due date
 * @param createdAt       when the invoice was created
 * @param updatedAt       when last updated
 */
public record InvoiceResponse(
        UUID invoiceId,
        UUID bookingId,
        UUID customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        int lineItemCount,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Factory method to create a response from a domain Invoice aggregate.
     *
     * @param invoice the domain invoice
     * @return the response DTO
     */
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getInvoiceId(),
                invoice.getBookingId(),
                invoice.getCustomerId(),
                invoice.getStatus().name(),
                invoice.getTotalAmount().amount(),
                invoice.getTotalAmount().currency(),
                invoice.getLineItems().size(),
                invoice.getDueDate(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
