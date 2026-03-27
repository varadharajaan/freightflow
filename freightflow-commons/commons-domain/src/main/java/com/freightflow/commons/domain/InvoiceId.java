package com.freightflow.commons.domain;

import java.util.UUID;

/**
 * Strongly-typed identifier for invoice aggregates.
 *
 * <p>Ensures type safety at compile time — an {@code InvoiceId} cannot be confused
 * with a {@code BookingId}, {@code CustomerId}, or any other identifier type.</p>
 *
 * @param value the underlying UUID
 */
public record InvoiceId(UUID value) implements DomainId {

    public InvoiceId {
        DomainId.requireNonNull(value, "InvoiceId");
    }

    /**
     * Creates a new random InvoiceId.
     *
     * @return a new InvoiceId with a random UUID
     */
    public static InvoiceId generate() {
        return new InvoiceId(UUID.randomUUID());
    }

    /**
     * Creates an InvoiceId from a string representation.
     *
     * @param value the string UUID
     * @return an InvoiceId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static InvoiceId fromString(String value) {
        return new InvoiceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "InvoiceId[%s]".formatted(value);
    }
}
