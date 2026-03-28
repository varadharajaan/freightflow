package com.freightflow.billingservice.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST request DTO for recording a payment against an invoice.
 *
 * @param invoiceId the invoice to pay
 * @param amount    the payment amount
 * @param currency  the currency code (e.g., USD)
 * @param method    the payment method (BANK_TRANSFER, CREDIT_CARD, WIRE)
 * @param reference the external payment reference
 */
public record RecordPaymentRequest(
        @NotNull UUID invoiceId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String method,
        @NotBlank String reference
) {
}
