package com.freightflow.booking.domain.model;

import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.domain.Weight;

import java.util.Objects;

/**
 * Value object representing the cargo details of a booking.
 *
 * <p>Cargo is immutable — any change to cargo details requires creating a new instance.
 * This follows the DDD Value Object pattern where equality is based on attribute values,
 * not identity.</p>
 *
 * @param commodityCode   HS (Harmonized System) commodity classification code
 * @param description     human-readable description of the cargo
 * @param weight          total weight of the cargo
 * @param containerType   type of container required
 * @param containerCount  number of containers needed
 * @param origin          origin port (UN/LOCODE)
 * @param destination     destination port (UN/LOCODE)
 */
public record Cargo(
        String commodityCode,
        String description,
        Weight weight,
        ContainerType containerType,
        int containerCount,
        PortCode origin,
        PortCode destination
) {

    /**
     * Compact canonical constructor with fail-fast validation.
     */
    public Cargo {
        Objects.requireNonNull(commodityCode, "Commodity code must not be null");
        Objects.requireNonNull(description, "Description must not be null");
        Objects.requireNonNull(weight, "Weight must not be null");
        Objects.requireNonNull(containerType, "Container type must not be null");
        Objects.requireNonNull(origin, "Origin port must not be null");
        Objects.requireNonNull(destination, "Destination port must not be null");

        if (containerCount <= 0) {
            throw new IllegalArgumentException("Container count must be positive, got: " + containerCount);
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException(
                    "Origin and destination must differ: both are '%s'".formatted(origin.value()));
        }
    }

    /**
     * Calculates the total TEU (Twenty-foot Equivalent Units) for this cargo.
     *
     * @return the total TEU
     */
    public double totalTeu() {
        return containerType.teuFactor() * containerCount;
    }
}
