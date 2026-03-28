package com.freightflow.commons.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Weight} value object.
 *
 * <p>Tests cover creation in different units, conversion between KG and LBS,
 * and rejection of zero/negative values.</p>
 *
 * @see Weight
 * @see Weight.WeightUnit
 */
@DisplayName("Weight Value Object")
class WeightTest {

    // ==================== Creation Tests ====================

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create Weight in kilograms")
        void should_CreateWeight_When_ValidKilograms() {
            // When
            Weight weight = Weight.ofKilograms(new BigDecimal("1000.5"));

            // Then
            assertThat(weight.value()).isEqualByComparingTo(new BigDecimal("1000.5"));
            assertThat(weight.unit()).isEqualTo(Weight.WeightUnit.KG);
        }

        @Test
        @DisplayName("should create Weight in pounds")
        void should_CreateWeight_When_ValidPounds() {
            // When
            Weight weight = Weight.ofPounds(new BigDecimal("2204.62"));

            // Then
            assertThat(weight.value()).isEqualByComparingTo(new BigDecimal("2204.62"));
            assertThat(weight.unit()).isEqualTo(Weight.WeightUnit.LBS);
        }

        @Test
        @DisplayName("should throw exception when value is null")
        void should_ThrowException_When_ValueNull() {
            assertThatThrownBy(() -> Weight.ofKilograms(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Weight value must not be null");
        }

        @Test
        @DisplayName("should throw exception when unit is null")
        void should_ThrowException_When_UnitNull() {
            assertThatThrownBy(() -> new Weight(new BigDecimal("100"), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Weight unit must not be null");
        }

        @ParameterizedTest(name = "value={0} should be rejected (must be > 0)")
        @MethodSource("nonPositiveValues")
        @DisplayName("should reject zero and negative values")
        void should_ThrowException_When_ValueNotPositive(BigDecimal value) {
            assertThatThrownBy(() -> Weight.ofKilograms(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Weight value must be positive");
        }

        static Stream<BigDecimal> nonPositiveValues() {
            return Stream.of(
                    BigDecimal.ZERO,
                    new BigDecimal("-1"),
                    new BigDecimal("-0.001"),
                    new BigDecimal("-9999")
            );
        }
    }

    // ==================== Conversion Tests ====================

    @Nested
    @DisplayName("KG ↔ LBS Conversion")
    class Conversion {

        @Test
        @DisplayName("should convert pounds to kilograms")
        void should_ConvertToKg_When_WeightInPounds() {
            // Given — 100 LBS ≈ 45.359237 KG
            Weight pounds = Weight.ofPounds(new BigDecimal("100"));

            // When
            Weight kilograms = pounds.toKilograms();

            // Then
            assertThat(kilograms.unit()).isEqualTo(Weight.WeightUnit.KG);
            assertThat(kilograms.value()).isEqualByComparingTo(new BigDecimal("45.359237"));
        }

        @Test
        @DisplayName("should return same instance when already in kilograms")
        void should_ReturnSameInstance_When_AlreadyKg() {
            // Given
            Weight kg = Weight.ofKilograms(new BigDecimal("500"));

            // When
            Weight converted = kg.toKilograms();

            // Then
            assertThat(converted).isSameAs(kg);
        }

        @ParameterizedTest(name = "{0} LBS = {1} KG")
        @MethodSource("poundsToKgConversions")
        @DisplayName("should correctly convert various LBS values to KG")
        void should_ConvertCorrectly_When_GivenPoundsValue(
                BigDecimal lbs, BigDecimal expectedKg) {

            Weight weight = Weight.ofPounds(lbs);
            Weight converted = weight.toKilograms();

            assertThat(converted.value())
                    .as("%s LBS should be %s KG", lbs, expectedKg)
                    .isEqualByComparingTo(expectedKg);
        }

        static Stream<Arguments> poundsToKgConversions() {
            return Stream.of(
                    // 1 LBS = 0.453592 KG (rounded to 6 decimal places)
                    Arguments.of(new BigDecimal("1"), new BigDecimal("0.453592")),
                    // 1000 LBS ≈ 453.592370 KG
                    Arguments.of(new BigDecimal("1000"), new BigDecimal("453.592370")),
                    // 2204.62 LBS ≈ 999.998952 KG (close to 1000 KG)
                    Arguments.of(new BigDecimal("2204.62"), new BigDecimal("999.998952"))
            );
        }
    }

    // ==================== ToString Tests ====================

    @Nested
    @DisplayName("String Representation")
    class StringRepresentation {

        @Test
        @DisplayName("should format as 'value unit'")
        void should_FormatCorrectly_When_ToStringCalled() {
            Weight weight = Weight.ofKilograms(new BigDecimal("1500"));

            assertThat(weight.toString()).isEqualTo("1500 KG");
        }

        @Test
        @DisplayName("should format pounds correctly")
        void should_FormatPoundsCorrectly_When_ToStringCalled() {
            Weight weight = Weight.ofPounds(new BigDecimal("3307.00"));

            assertThat(weight.toString()).isEqualTo("3307.00 LBS");
        }
    }

    // ==================== Equality Tests ====================

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when value and unit match")
        void should_BeEqual_When_SameValueAndUnit() {
            Weight a = Weight.ofKilograms(new BigDecimal("1000"));
            Weight b = Weight.ofKilograms(new BigDecimal("1000"));

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("should not be equal when units differ")
        void should_NotBeEqual_When_DifferentUnits() {
            Weight kg = Weight.ofKilograms(new BigDecimal("100"));
            Weight lbs = Weight.ofPounds(new BigDecimal("100"));

            assertThat(kg).isNotEqualTo(lbs);
        }
    }
}
