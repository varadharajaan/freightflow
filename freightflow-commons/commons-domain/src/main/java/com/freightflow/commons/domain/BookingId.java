package com.freightflow.commons.domain;

import java.util.UUID;

/**
 * Strongly-typed identifier for booking aggregates.
 *
 * <p>Ensures type safety at compile time — a {@code BookingId} cannot be confused
 * with a {@code CustomerId} or any other identifier type (Liskov Substitution Principle).</p>
 *
 * @param value the underlying UUID
 */
public record BookingId(UUID value) implements DomainId {

    public BookingId {
        DomainId.requireNonNull(value, "BookingId");
    }

    /**
     * Creates a new random BookingId.
     *
     * @return a new BookingId with a random UUID
     */
    public static BookingId generate() {
        return new BookingId(UUID.randomUUID());
    }

    /**
     * Creates a BookingId from a string representation.
     *
     * @param value the string UUID
     * @return a BookingId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static BookingId fromString(String value) {
        return new BookingId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "BookingId[%s]".formatted(value);
    }
}
