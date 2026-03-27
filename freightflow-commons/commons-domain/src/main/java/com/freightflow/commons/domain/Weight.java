package com.freightflow.commons.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object representing a physical weight with its unit of measurement.
 *
 * <p>Follows the Value Object pattern from Domain-Driven Design — instances are immutable,
 * compared by value, and self-validating. A {@code Weight} always represents a positive
 * quantity paired with a {@link WeightUnit}.</p>
 *
 * <p>Supports conversion between kilograms and pounds via {@link #toKilograms()}.</p>
 *
 * @param value the weight value (must be {@code > 0})
 * @param unit  the unit of measurement (must not be {@code null})
 */
public record Weight(BigDecimal value, WeightUnit unit) {

    public Weight {
        Objects.requireNonNull(value, "Weight value must not be null");
        Objects.requireNonNull(unit, "Weight unit must not be null");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Weight value must be positive, got: " + value);
        }
    }

    /**
     * Unit of measurement for weight, with conversion factors to kilograms.
     */
    public enum WeightUnit {

        /** Kilograms — SI base unit for mass. */
        KG(BigDecimal.ONE),

        /** Pounds — imperial unit; 1 lb ≈ 0.45359237 kg. */
        LBS(new BigDecimal("0.45359237"));

        private final BigDecimal toKgFactor;

        WeightUnit(BigDecimal toKgFactor) {
            this.toKgFactor = toKgFactor;
        }

        /**
         * Returns the conversion factor from this unit to kilograms.
         *
         * @return the multiplication factor to convert a value in this unit to kilograms
         */
        public BigDecimal toKgFactor() {
            return toKgFactor;
        }
    }

    /**
     * Creates a {@code Weight} in kilograms.
     *
     * @param kg the weight in kilograms
     * @return a new {@code Weight} instance in KG
     * @throws IllegalArgumentException if the value is not positive
     */
    public static Weight ofKilograms(BigDecimal kg) {
        return new Weight(kg, WeightUnit.KG);
    }

    /**
     * Creates a {@code Weight} in pounds.
     *
     * @param lbs the weight in pounds
     * @return a new {@code Weight} instance in LBS
     * @throws IllegalArgumentException if the value is not positive
     */
    public static Weight ofPounds(BigDecimal lbs) {
        return new Weight(lbs, WeightUnit.LBS);
    }

    /**
     * Converts this weight to kilograms, regardless of the current unit.
     *
     * <p>If the weight is already in kilograms, the same value is returned (wrapped in a new instance).
     * Otherwise, the value is multiplied by the unit's conversion factor, rounded to 6 decimal places.</p>
     *
     * @return a new {@code Weight} expressed in kilograms
     */
    public Weight toKilograms() {
        if (unit == WeightUnit.KG) {
            return this;
        }
        BigDecimal converted = value.multiply(unit.toKgFactor())
                .setScale(6, RoundingMode.HALF_UP);
        return new Weight(converted, WeightUnit.KG);
    }

    @Override
    public String toString() {
        return "%s %s".formatted(value.toPlainString(), unit);
    }
}
