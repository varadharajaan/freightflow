package com.freightflow.booking.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound REST DTO for cancelling a booking.
 *
 * @param reason the reason for cancellation
 */
public record CancelBookingRequest(

        @NotBlank(message = "Cancellation reason is required")
        String reason
) {
}
