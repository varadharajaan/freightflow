package com.freightflow.commons.domain;

import java.util.UUID;

/**
 * Strongly-typed identifier for customer aggregates.
 *
 * @param value the underlying UUID
 */
public record CustomerId(UUID value) implements DomainId {

    public CustomerId {
        DomainId.requireNonNull(value, "CustomerId");
    }

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId fromString(String value) {
        return new CustomerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "CustomerId[%s]".formatted(value);
    }
}
