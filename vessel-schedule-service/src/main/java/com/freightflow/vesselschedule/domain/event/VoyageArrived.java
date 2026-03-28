package com.freightflow.vesselschedule.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a voyage arrives at its final destination port.
 *
 * <p>Consumers: tracking-service (container arrived at port), booking-service
 * (mark bookings as DELIVERED), notification-service (arrival notification).</p>
 *
 * @param eventId     unique event identifier
 * @param voyageId    the voyage that arrived
 * @param vesselId    the vessel on the voyage
 * @param arrivalPort the port of arrival
 * @param occurredAt  when the arrival occurred
 */
public record VoyageArrived(
        UUID eventId,
        UUID voyageId,
        UUID vesselId,
        String arrivalPort,
        Instant occurredAt
) implements VesselEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public VoyageArrived(UUID voyageId, UUID vesselId, String arrivalPort, Instant occurredAt) {
        this(UUID.randomUUID(), voyageId, vesselId, arrivalPort, occurredAt);
    }
}
