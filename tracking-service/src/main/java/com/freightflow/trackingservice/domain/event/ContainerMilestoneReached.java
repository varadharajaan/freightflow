package com.freightflow.trackingservice.domain.event;

import com.freightflow.trackingservice.domain.model.Milestone;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a container reaches a significant logistics milestone.
 *
 * <p>Carries all data downstream consumers need (Event-Carried State Transfer).
 * Consumers: notification-service (milestone alerts), booking-service (status update).</p>
 *
 * @param eventId     unique event identifier
 * @param containerId the container that reached the milestone
 * @param milestone   the milestone details
 * @param occurredAt  when the milestone was reached
 */
public record ContainerMilestoneReached(
        UUID eventId,
        String containerId,
        Milestone milestone,
        Instant occurredAt
) implements TrackingEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public ContainerMilestoneReached(String containerId, Milestone milestone) {
        this(UUID.randomUUID(), containerId, milestone, milestone.timestamp());
    }
}
