package com.freightflow.trackingservice.domain.event;

import com.freightflow.trackingservice.domain.model.Position;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a container's position is updated.
 *
 * <p>Carries all data downstream consumers need (Event-Carried State Transfer).
 * Consumers: notification-service (geofence alerts), WebSocket subscribers (live map).</p>
 *
 * @param eventId     unique event identifier
 * @param containerId the container whose position was updated
 * @param position    the new position reading
 * @param occurredAt  when the position was recorded
 */
public record ContainerPositionUpdated(
        UUID eventId,
        String containerId,
        Position position,
        Instant occurredAt) implements TrackingEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public ContainerPositionUpdated(String containerId, Position position, Instant occurredAt) {
        this(UUID.randomUUID(), containerId, position, occurredAt);
    }
}
