package com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto;

import com.freightflow.trackingservice.domain.model.Container;

import java.time.Instant;
import java.util.UUID;

/**
 * REST response DTO for container tracking data.
 *
 * <p>Maps domain model fields to a flat JSON-friendly structure. This DTO is returned
 * by the tracking REST API and decouples the domain model from the HTTP contract.</p>
 *
 * @param containerId     the ISO container identifier
 * @param bookingId       the associated booking UUID
 * @param status          the current container status
 * @param latitude        the current latitude (null if no position)
 * @param longitude       the current longitude (null if no position)
 * @param positionSource  the source of the position reading (null if no position)
 * @param voyageId        the assigned voyage UUID (null if not on voyage)
 * @param milestoneCount  the number of milestones reached
 * @param createdAt       when tracking started
 * @param updatedAt       when last updated
 */
public record ContainerResponse(
        String containerId,
        UUID bookingId,
        String status,
        Double latitude,
        Double longitude,
        String positionSource,
        UUID voyageId,
        int milestoneCount,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Factory method to create a response from a domain Container aggregate.
     *
     * @param container the domain container
     * @return the response DTO
     */
    public static ContainerResponse from(Container container) {
        return new ContainerResponse(
                container.getContainerId(),
                container.getBookingId(),
                container.getStatus().name(),
                container.getCurrentPosition().map(p -> p.latitude()).orElse(null),
                container.getCurrentPosition().map(p -> p.longitude()).orElse(null),
                container.getCurrentPosition().map(p -> p.source().name()).orElse(null),
                container.getVoyageId().orElse(null),
                container.getMilestones().size(),
                container.getCreatedAt(),
                container.getUpdatedAt()
        );
    }
}
