package com.freightflow.commons.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for strongly-typed domain identifiers.
 *
 * <p>Follows the Value Object pattern from Domain-Driven Design. Identifiers are immutable
 * and compared by value, not by reference. Using dedicated types instead of raw {@code String}
 * or {@code UUID} prevents accidental parameter swapping (Liskov Substitution Principle).</p>
 *
 * <p>Example: You cannot accidentally pass a {@code CustomerId} where a {@code BookingId}
 * is expected — the compiler catches it.</p>
 *
 * @param value the underlying UUID value
 * @see BookingId
 * @see CustomerId
 * @see ContainerId
 */
public sealed interface DomainId extends Serializable
        permits BookingId, CustomerId, ContainerId, VesselId, VoyageId, InvoiceId {

    UUID value();

    /**
     * Returns the string representation of the underlying UUID.
     *
     * @return the UUID as a string
     */
    default String asString() {
        return value().toString();
    }

    /**
     * Validates that the given value is not null.
     *
     * @param value the UUID to validate
     * @throws IllegalArgumentException if value is null
     */
    static void requireNonNull(UUID value, String typeName) {
        Objects.requireNonNull(value, typeName + " value must not be null");
    }
}
