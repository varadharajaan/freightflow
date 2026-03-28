package com.freightflow.customerservice.domain.model;

import java.util.Objects;

/**
 * Value object representing a physical mailing address.
 *
 * <p>Follows the DDD Value Object pattern — instances are immutable and
 * compared by value. An {@code Address} is always fully specified with
 * all required fields.</p>
 *
 * @param street     the street address line
 * @param city       the city name
 * @param state      the state or province
 * @param postalCode the postal or ZIP code
 * @param country    the ISO 3166-1 alpha-2 country code (e.g. "US", "CN")
 */
public record Address(
        String street,
        String city,
        String state,
        String postalCode,
        String country
) {

    /**
     * Compact canonical constructor with fail-fast validation.
     */
    public Address {
        Objects.requireNonNull(street, "Street must not be null");
        Objects.requireNonNull(city, "City must not be null");
        Objects.requireNonNull(state, "State must not be null");
        Objects.requireNonNull(postalCode, "Postal code must not be null");
        Objects.requireNonNull(country, "Country must not be null");

        if (street.isBlank()) {
            throw new IllegalArgumentException("Street must not be blank");
        }
        if (country.length() != 2) {
            throw new IllegalArgumentException(
                    "Country must be a 2-letter ISO code, got: '%s'".formatted(country));
        }
    }

    @Override
    public String toString() {
        return "%s, %s, %s %s, %s".formatted(street, city, state, postalCode, country);
    }
}
