package com.freightflow.booking.domain.model;

import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.domain.Weight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Cargo} value object.
 *
 * <p>Tests cover validation rules (null checks, negative container count, same
 * origin/destination) and TEU calculation for various container types.</p>
 *
 * @see Cargo
 * @see ContainerType
 */
@DisplayName("Cargo Value Object")
class CargoTest {

    // ==================== Test Fixtures ====================

    private static final PortCode ORIGIN = PortCode.of("DEHAM");
    private static final PortCode DESTINATION = PortCode.of("CNSHA");
    private static final Weight VALID_WEIGHT = Weight.ofKilograms(new BigDecimal("10000"));

    // ==================== Validation Tests ====================

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should create cargo when all inputs are valid")
        void should_CreateCargo_When_AllInputsValid() {
            // When
            Cargo cargo = new Cargo(
                    "HS8471", "Electronics", VALID_WEIGHT,
                    ContainerType.DRY_40, 2, ORIGIN, DESTINATION
            );

            // Then
            assertThat(cargo.commodityCode()).isEqualTo("HS8471");
            assertThat(cargo.description()).isEqualTo("Electronics");
            assertThat(cargo.weight()).isEqualTo(VALID_WEIGHT);
            assertThat(cargo.containerType()).isEqualTo(ContainerType.DRY_40);
            assertThat(cargo.containerCount()).isEqualTo(2);
            assertThat(cargo.origin()).isEqualTo(ORIGIN);
            assertThat(cargo.destination()).isEqualTo(DESTINATION);
        }

        @Test
        @DisplayName("should throw NullPointerException when commodity code is null")
        void should_ThrowException_When_CommodityCodeNull() {
            assertThatThrownBy(() -> new Cargo(
                    null, "Electronics", VALID_WEIGHT,
                    ContainerType.DRY_40, 2, ORIGIN, DESTINATION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Commodity code must not be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when description is null")
        void should_ThrowException_When_DescriptionNull() {
            assertThatThrownBy(() -> new Cargo(
                    "HS8471", null, VALID_WEIGHT,
                    ContainerType.DRY_40, 2, ORIGIN, DESTINATION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Description must not be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when weight is null")
        void should_ThrowException_When_WeightNull() {
            assertThatThrownBy(() -> new Cargo(
                    "HS8471", "Electronics", null,
                    ContainerType.DRY_40, 2, ORIGIN, DESTINATION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Weight must not be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when container type is null")
        void should_ThrowException_When_ContainerTypeNull() {
            assertThatThrownBy(() -> new Cargo(
                    "HS8471", "Electronics", VALID_WEIGHT,
                    null, 2, ORIGIN, DESTINATION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Container type must not be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when origin is null")
        void should_ThrowException_When_OriginNull() {
            assertThatThrownBy(() -> new Cargo(
                    "HS8471", "Electronics", VALID_WEIGHT,
                    ContainerType.DRY_40, 2, null, DESTINATION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Origin port must not be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when destination is null")
        void should_ThrowException_When_DestinationNull() {
            assertThatThrownBy(() -> new Cargo(
                    "HS8471", "Electronics", VALID_WEIGHT,
                    ContainerType.DRY_40, 2, ORIGIN, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Destination port must not be null");
        }

        @ParameterizedTest(name = "containerCount={0} should be rejected")
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("should throw exception when container count is not positive")
        void should_ThrowException_When_ContainerCountNotPositive(int count) {
            assertThatThrownBy(() -> new Cargo(
                    "HS8471", "Electronics", VALID_WEIGHT,
                    ContainerType.DRY_40, count, ORIGIN, DESTINATION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Container count must be positive");
        }

        @Test
        @DisplayName("should throw exception when origin and destination are the same")
        void should_ThrowException_When_OriginEqualsDestination() {
            PortCode samePort = PortCode.of("DEHAM");

            assertThatThrownBy(() -> new Cargo(
                    "HS8471", "Electronics", VALID_WEIGHT,
                    ContainerType.DRY_40, 2, samePort, samePort))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Origin and destination must differ");
        }
    }

    // ==================== TEU Calculation Tests ====================

    @Nested
    @DisplayName("TEU Calculation")
    class TeuCalculation {

        /**
         * Provides container type, count, and expected TEU combinations.
         */
        static Stream<Arguments> teuCalculationCases() {
            return Stream.of(
                    Arguments.of(ContainerType.DRY_20, 1, 1.0),
                    Arguments.of(ContainerType.DRY_20, 5, 5.0),
                    Arguments.of(ContainerType.DRY_40, 1, 2.0),
                    Arguments.of(ContainerType.DRY_40, 3, 6.0),
                    Arguments.of(ContainerType.REEFER_20, 2, 2.0),
                    Arguments.of(ContainerType.REEFER_40, 4, 8.0),
                    Arguments.of(ContainerType.HIGH_CUBE_40, 2, 4.0),
                    Arguments.of(ContainerType.OPEN_TOP_20, 10, 10.0)
            );
        }

        @ParameterizedTest(name = "{0} x {1} = {2} TEU")
        @MethodSource("teuCalculationCases")
        @DisplayName("should calculate correct TEU for container type and count")
        void should_CalculateCorrectTeu_When_GivenContainerTypeAndCount(
                ContainerType containerType, int count, double expectedTeu) {

            Cargo cargo = new Cargo(
                    "HS8471", "Goods", VALID_WEIGHT,
                    containerType, count, ORIGIN, DESTINATION
            );

            assertThat(cargo.totalTeu())
                    .as("TEU for %d x %s", count, containerType)
                    .isEqualTo(expectedTeu);
        }
    }
}
