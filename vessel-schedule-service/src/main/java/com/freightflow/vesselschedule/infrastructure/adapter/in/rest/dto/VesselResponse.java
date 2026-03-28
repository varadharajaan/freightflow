package com.freightflow.vesselschedule.infrastructure.adapter.in.rest.dto;

import com.freightflow.vesselschedule.domain.model.Vessel;

import java.util.UUID;

/**
 * REST response DTO for vessel data.
 *
 * @param vesselId    the vessel UUID
 * @param name        the vessel name
 * @param imoNumber   the IMO identification number
 * @param flag        the vessel flag state
 * @param capacityTeu the maximum TEU capacity
 * @param status      the current vessel status
 */
public record VesselResponse(
        UUID vesselId,
        String name,
        String imoNumber,
        String flag,
        int capacityTeu,
        String status
) {

    /**
     * Factory method to create a response from a domain Vessel aggregate.
     *
     * @param vessel the domain vessel
     * @return the response DTO
     */
    public static VesselResponse from(Vessel vessel) {
        return new VesselResponse(
                vessel.getVesselId(),
                vessel.getName(),
                vessel.getImoNumber(),
                vessel.getFlag(),
                vessel.getCapacityTeu(),
                vessel.getStatus().name()
        );
    }
}
