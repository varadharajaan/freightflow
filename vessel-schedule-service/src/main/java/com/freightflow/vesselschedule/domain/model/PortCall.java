package com.freightflow.vesselschedule.domain.model;

import java.time.Instant;

/**
 * Value object representing a scheduled port call in a voyage itinerary.
 *
 * <p>Each port call records estimated and actual arrival/departure times
 * at a specific port. Estimated times are set during voyage planning;
 * actual times are recorded when the vessel arrives or departs.</p>
 *
 * @param port               the UN/LOCODE port code
 * @param estimatedArrival   the planned arrival time
 * @param estimatedDeparture the planned departure time
 * @param actualArrival      the actual arrival time (null until arrived)
 * @param actualDeparture    the actual departure time (null until departed)
 */
public record PortCall(
        String port,
        Instant estimatedArrival,
        Instant estimatedDeparture,
        Instant actualArrival,
        Instant actualDeparture
) {

    /**
     * Creates a planned port call (no actual times yet).
     *
     * @param port               the port code
     * @param estimatedArrival   the planned arrival time
     * @param estimatedDeparture the planned departure time
     * @return a new PortCall with estimated times only
     */
    public static PortCall planned(String port, Instant estimatedArrival, Instant estimatedDeparture) {
        return new PortCall(port, estimatedArrival, estimatedDeparture, null, null);
    }

    /**
     * Records the actual arrival time.
     *
     * @param arrivalTime the actual arrival time
     * @return a new PortCall with the actual arrival recorded
     */
    public PortCall withActualArrival(Instant arrivalTime) {
        return new PortCall(port, estimatedArrival, estimatedDeparture, arrivalTime, actualDeparture);
    }

    /**
     * Records the actual departure time.
     *
     * @param departureTime the actual departure time
     * @return a new PortCall with the actual departure recorded
     */
    public PortCall withActualDeparture(Instant departureTime) {
        return new PortCall(port, estimatedArrival, estimatedDeparture, actualArrival, departureTime);
    }

    /**
     * Checks whether the vessel has arrived at this port.
     *
     * @return true if actual arrival is recorded
     */
    public boolean hasArrived() {
        return actualArrival != null;
    }

    /**
     * Checks whether the vessel has departed from this port.
     *
     * @return true if actual departure is recorded
     */
    public boolean hasDeparted() {
        return actualDeparture != null;
    }
}
