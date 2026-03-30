package com.freightflow.vesselschedule.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Inbound REST DTO for vessel capacity reservation and release requests.
 *
 * <p>This request supports idempotent capacity operations — the same {@code idempotencyKey}
 * can be sent multiple times without duplicating the reservation or release.
 * This is critical in distributed systems where network failures may cause retries.</p>
 *
 * @param teu            the number of TEU to reserve or release (must be positive)
 * @param bookingId      the booking identifier associated with this capacity operation
 * @param idempotencyKey a unique key for deduplication of the request
 */
public record CapacityRequest(

        @Positive(message = "TEU must be positive")
        double teu,

        @NotBlank(message = "Booking ID is required")
        String bookingId,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey
) {
}
