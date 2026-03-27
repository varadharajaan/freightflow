package com.freightflow.commons.domain;

import java.util.UUID;

/**
 * Strongly-typed identifier for container aggregates.
 *
 * <p>Ensures type safety at compile time — a {@code ContainerId} cannot be confused
 * with a {@code BookingId}, {@code VesselId}, or any other identifier type.</p>
 *
 * @param value the underlying UUID
 */
public record ContainerId(UUID value) implements DomainId {

    public ContainerId {
        DomainId.requireNonNull(value, "ContainerId");
    }

    /**
     * Creates a new random ContainerId.
     *
     * @return a new ContainerId with a random UUID
     */
    public static ContainerId generate() {
        return new ContainerId(UUID.randomUUID());
    }

    /**
     * Creates a ContainerId from a string representation.
     *
     * @param value the string UUID
     * @return a ContainerId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static ContainerId fromString(String value) {
        return new ContainerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ContainerId[%s]".formatted(value);
    }
}
