package com.freightflow.vesselschedule.infrastructure.adapter.in.rest.dto;

import com.freightflow.vesselschedule.domain.model.Voyage;

import java.time.Instant;
import java.util.UUID;

/**
 * REST response DTO for voyage data.
 *
 * @param voyageId            the voyage UUID
 * @param vesselId            the vessel UUID
 * @param voyageNumber        the voyage number
 * @param status              the current voyage status
 * @param totalCapacityTeu    the total TEU capacity
 * @param remainingCapacityTeu the remaining TEU capacity
 * @param portCallCount       the number of port calls in the route
 * @param createdAt           when the voyage was created
 * @param updatedAt           when last updated
 */
public record VoyageResponse(
        UUID voyageId,
        UUID vesselId,
        String voyageNumber,
        String status,
        int totalCapacityTeu,
        int remainingCapacityTeu,
        int portCallCount,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Factory method to create a response from a domain Voyage aggregate.
     *
     * @param voyage the domain voyage
     * @return the response DTO
     */
    public static VoyageResponse from(Voyage voyage) {
        return new VoyageResponse(
                voyage.getVoyageId(),
                voyage.getVesselId(),
                voyage.getVoyageNumber(),
                voyage.getStatus().name(),
                voyage.getTotalCapacityTeu(),
                voyage.getRemainingCapacityTeu(),
                voyage.getRoute().size(),
                voyage.getCreatedAt(),
                voyage.getUpdatedAt()
        );
    }
}
