package com.freightflow.vesselschedule.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a voyage departs from its origin port.
 *
 * <p>Consumers: tracking-service (start live AIS tracking), booking-service
 * (mark bookings as SHIPPED), notification-service (departure notification).</p>
 *
 * @param eventId       unique event identifier
 * @param voyageId      the voyage that departed
 * @param vesselId      the vessel on the voyage
 * @param departurePort the port of departure
 * @param occurredAt    when the departure occurred
 */
public record VoyageDeparted(
        UUID eventId,
        UUID voyageId,
        UUID vesselId,
        String departurePort,
        Instant occurredAt
) implements VesselEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public VoyageDeparted(UUID voyageId, UUID vesselId, String departurePort, Instant occurredAt) {
        this(UUID.randomUUID(), voyageId, vesselId, departurePort, occurredAt);
    }
}
