package com.freightflow.vesselschedule.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface for all vessel/voyage domain events.
 *
 * <p>Using a sealed interface ensures that all event types are explicitly defined
 * and can be exhaustively matched in switch expressions (Java 21 pattern matching).
 * This enforces the Liskov Substitution Principle.</p>
 *
 * <p>Events are immutable records carrying all data needed by downstream consumers.
 * They follow the Event-Carried State Transfer pattern.</p>
 *
 * @see VoyageDeparted
 * @see VoyageArrived
 * @see CapacityReserved
 */
public sealed interface VesselEvent
        permits VoyageDeparted, VoyageArrived, CapacityReserved {

    /**
     * Unique identifier for this event instance.
     *
     * @return the event ID
     */
    UUID eventId();

    /**
     * The voyage this event belongs to.
     *
     * @return the voyage ID
     */
    UUID voyageId();

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
