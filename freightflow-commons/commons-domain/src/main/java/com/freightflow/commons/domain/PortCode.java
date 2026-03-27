package com.freightflow.commons.domain;

import java.util.Objects;

/**
 * Value Object representing a UN/LOCODE port code.
 *
 * <p>A UN/LOCODE is a five-character code used to identify ports and other locations
 * relevant to international trade and transport. The first two characters represent the
 * country (ISO 3166-1 alpha-2), followed by three characters for the location.</p>
 *
 * <p>Examples:</p>
 * <ul>
 *     <li>{@code DEHAM} — Hamburg, Germany</li>
 *     <li>{@code CNSHA} — Shanghai, China</li>
 *     <li>{@code USLAX} — Los Angeles, United States</li>
 * </ul>
 *
 * <p>Follows the Value Object pattern from Domain-Driven Design — instances are immutable,
 * compared by value, and self-validating.</p>
 *
 * @param value the five-character UN/LOCODE (uppercase)
 */
public record PortCode(String value) {

    public PortCode {
        Objects.requireNonNull(value, "PortCode value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PortCode value must not be blank");
        }
        if (value.length() != 5) {
            throw new IllegalArgumentException(
                    "PortCode must be exactly 5 characters (UN/LOCODE format), got: '%s' (%d chars)"
                            .formatted(value, value.length()));
        }
        value = value.toUpperCase();
    }

    /**
     * Creates a {@code PortCode} from the given string, normalising it to uppercase.
     *
     * @param value the port code string (must be exactly 5 characters)
     * @return a new {@code PortCode}
     * @throws IllegalArgumentException if the value is blank, null, or not exactly 5 characters
     */
    public static PortCode of(String value) {
        return new PortCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
