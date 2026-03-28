package com.freightflow.trackingservice.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface for all tracking domain events.
 *
 * <p>Using a sealed interface ensures that all event types are explicitly defined
 * and can be exhaustively matched in switch expressions (Java 21 pattern matching).
 * This enforces the Liskov Substitution Principle — any {@code TrackingEvent} subtype
 * is a valid substitution wherever a {@code TrackingEvent} is expected.</p>
 *
 * <p>Events are immutable records carrying all data needed by downstream consumers.
 * They follow the Event-Carried State Transfer pattern — consumers should NOT need
 * to call back to the tracking service for additional data.</p>
 *
 * @see ContainerPositionUpdated
 * @see ContainerMilestoneReached
 * @see ContainerDelivered
 */
public sealed interface TrackingEvent
        permits ContainerPositionUpdated, ContainerMilestoneReached, ContainerDelivered {

    /**
     * Unique identifier for this event instance.
     *
     * @return the event ID
     */
    UUID eventId();

    /**
     * The container this event belongs to.
     *
     * @return the container identifier (ISO format)
     */
    String containerId();

    /**
     * When this event occurred.
     *
     * @return the event timestamp
     */
    Instant occurredAt();

    /**
     * The event type name used for serialization and routing.
     *
     * @return the fully qualified event type name
     */
    default String eventType() {
        return this.getClass().getSimpleName();
    }
}
