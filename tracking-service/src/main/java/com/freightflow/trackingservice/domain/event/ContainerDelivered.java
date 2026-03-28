package com.freightflow.trackingservice.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a container is delivered to the consignee.
 *
 * <p>Carries all data downstream consumers need (Event-Carried State Transfer).
 * Consumers: booking-service (mark booking as DELIVERED), notification-service (delivery confirmation).</p>
 *
 * @param eventId     unique event identifier
 * @param containerId the container that was delivered
 * @param deliveredAt when the container was delivered
 * @param occurredAt  when this event was emitted
 */
public record ContainerDelivered(
        UUID eventId,
        String containerId,
        Instant deliveredAt,
        Instant occurredAt
) implements TrackingEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public ContainerDelivered(String containerId, Instant deliveredAt) {
        this(UUID.randomUUID(), containerId, deliveredAt, deliveredAt);
    }
}
