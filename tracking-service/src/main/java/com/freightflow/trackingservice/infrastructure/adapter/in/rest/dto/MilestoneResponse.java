package com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto;

import com.freightflow.trackingservice.domain.model.Milestone;

import java.time.Instant;

/**
 * REST response DTO for a container milestone.
 *
 * <p>Maps the domain {@link Milestone} record to a JSON-friendly structure.</p>
 *
 * @param milestoneType the type of milestone (GATE_IN, LOADED, DEPARTED, ARRIVED, GATE_OUT)
 * @param port          the port where the milestone occurred
 * @param timestamp     when the milestone occurred
 * @param description   human-readable description
 */
public record MilestoneResponse(
        String milestoneType,
        String port,
        Instant timestamp,
        String description
) {

    /**
     * Factory method to create a response from a domain Milestone.
     *
     * @param milestone the domain milestone
     * @return the response DTO
     */
    public static MilestoneResponse from(Milestone milestone) {
        return new MilestoneResponse(
                milestone.milestoneType().name(),
                milestone.port(),
                milestone.timestamp(),
                milestone.description()
        );
    }
}
