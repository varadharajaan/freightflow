package com.freightflow.booking.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound REST DTO for confirming a booking and assigning it to a voyage.
 *
 * @param voyageId the voyage to assign the booking to (UUID as string)
 */
public record ConfirmBookingRequest(

        @NotBlank(message = "Voyage ID is required")
        String voyageId
) {
}
