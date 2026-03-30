package com.freightflow.booking.infrastructure.adapter.in.rest.dto;

import com.freightflow.booking.domain.model.ContainerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Inbound REST DTO for requesting a freight shipping quote.
 *
 * <p>Validated at the controller layer via Jakarta Bean Validation before
 * being passed to the {@link com.freightflow.booking.application.QuoteService}.
 * This DTO is intentionally decoupled from the domain to allow the API contract
 * to evolve independently.</p>
 *
 * @param origin         origin port code (UN/LOCODE, e.g. "CNSHA")
 * @param destination    destination port code (UN/LOCODE, e.g. "USLAX")
 * @param containerType  type of container required
 * @param containerCount number of containers needed
 * @param customerId     UUID of the customer requesting the quote
 */
public record QuoteRequest(

        @NotBlank(message = "Origin port code is required")
        String origin,

        @NotBlank(message = "Destination port code is required")
        String destination,

        @NotNull(message = "Container type is required")
        ContainerType containerType,

        @NotNull(message = "Container count is required")
        @Positive(message = "Container count must be positive")
        Integer containerCount,

        @NotBlank(message = "Customer ID is required")
        String customerId
) {
}
