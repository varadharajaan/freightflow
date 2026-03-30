package com.freightflow.vesselschedule.infrastructure.adapter.in.rest.dto;

import java.util.UUID;

/**
 * Outbound REST DTO for vessel capacity operation responses.
 *
 * <p>Returned by capacity reservation, release, and query endpoints to provide
 * the current state of a voyage's capacity after the operation.</p>
 *
 * @param voyageId             the voyage UUID
 * @param totalCapacityTeu     the total TEU capacity of the voyage
 * @param remainingCapacityTeu the remaining available TEU capacity
 * @param reserved             whether a reservation was successfully made (for reserve/release operations)
 */
public record CapacityResponse(
        UUID voyageId,
        int totalCapacityTeu,
        int remainingCapacityTeu,
        boolean reserved
) {
}
