package com.freightflow.commons.domain;

import java.util.UUID;

/**
 * Strongly-typed identifier for voyage aggregates.
 *
 * <p>Ensures type safety at compile time — a {@code VoyageId} cannot be confused
 * with a {@code BookingId}, {@code VesselId}, or any other identifier type.</p>
 *
 * @param value the underlying UUID
 */
public record VoyageId(UUID value) implements DomainId {

    public VoyageId {
        DomainId.requireNonNull(value, "VoyageId");
    }

    /**
     * Creates a new random VoyageId.
     *
     * @return a new VoyageId with a random UUID
     */
    public static VoyageId generate() {
        return new VoyageId(UUID.randomUUID());
    }

    /**
     * Creates a VoyageId from a string representation.
     *
     * @param value the string UUID
     * @return a VoyageId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static VoyageId fromString(String value) {
        return new VoyageId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "VoyageId[%s]".formatted(value);
    }
}
