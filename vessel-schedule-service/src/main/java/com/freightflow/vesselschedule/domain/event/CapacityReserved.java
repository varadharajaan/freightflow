package com.freightflow.vesselschedule.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when capacity is reserved on a voyage for a booking.
 *
 * <p>Consumers: booking-service (confirm capacity allocation).</p>
 *
 * @param eventId            unique event identifier
 * @param voyageId           the voyage with reserved capacity
 * @param bookingId          the booking the capacity was reserved for
 * @param teuReserved        the number of TEU reserved
 * @param remainingCapacity  the remaining TEU after reservation
 * @param occurredAt         when the reservation occurred
 */
public record CapacityReserved(
        UUID eventId,
        UUID voyageId,
        UUID bookingId,
        int teuReserved,
        int remainingCapacity,
        Instant occurredAt
) implements VesselEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public CapacityReserved(UUID voyageId, UUID bookingId, int teuReserved,
                            int remainingCapacity, Instant occurredAt) {
        this(UUID.randomUUID(), voyageId, bookingId, teuReserved, remainingCapacity, occurredAt);
    }
}
