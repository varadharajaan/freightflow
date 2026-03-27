package com.freightflow.commons.domain;

import java.util.UUID;

/**
 * Strongly-typed identifier for vessel aggregates.
 *
 * <p>Ensures type safety at compile time — a {@code VesselId} cannot be confused
 * with a {@code BookingId}, {@code ContainerId}, or any other identifier type.</p>
 *
 * @param value the underlying UUID
 */
public record VesselId(UUID value) implements DomainId {

    public VesselId {
        DomainId.requireNonNull(value, "VesselId");
    }

    /**
     * Creates a new random VesselId.
     *
     * @return a new VesselId with a random UUID
     */
    public static VesselId generate() {
        return new VesselId(UUID.randomUUID());
    }

    /**
     * Creates a VesselId from a string representation.
     *
     * @param value the string UUID
     * @return a VesselId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static VesselId fromString(String value) {
        return new VesselId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "VesselId[%s]".formatted(value);
    }
}
