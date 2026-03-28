package com.freightflow.commons.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link PortCode} value object.
 *
 * <p>Tests cover valid UN/LOCODE codes, normalisation to uppercase,
 * and rejection of invalid inputs (too short, too long, blank, null).</p>
 *
 * @see PortCode
 */
@DisplayName("PortCode Value Object")
class PortCodeTest {

    // ==================== Valid Codes ====================

    @Nested
    @DisplayName("Valid Codes")
    class ValidCodes {

        @ParameterizedTest(name = "''{0}'' should be a valid port code")
        @ValueSource(strings = {"DEHAM", "CNSHA", "USLAX", "NLRTM", "SGSIN"})
        @DisplayName("should accept valid 5-character uppercase codes")
        void should_AcceptCode_When_ValidFiveCharUppercase(String code) {
            PortCode portCode = PortCode.of(code);

            assertThat(portCode.value()).isEqualTo(code);
        }

        @Test
        @DisplayName("should normalise lowercase to uppercase")
        void should_NormaliseToUppercase_When_LowercaseProvided() {
            PortCode portCode = PortCode.of("deham");

            assertThat(portCode.value()).isEqualTo("DEHAM");
        }

        @Test
        @DisplayName("should normalise mixed case to uppercase")
        void should_NormaliseToUppercase_When_MixedCaseProvided() {
            PortCode portCode = PortCode.of("CnShA");

            assertThat(portCode.value()).isEqualTo("CNSHA");
        }
    }

    // ==================== Invalid Codes ====================

    @Nested
    @DisplayName("Invalid Codes")
    class InvalidCodes {

        @Test
        @DisplayName("should throw exception when value is null")
        void should_ThrowException_When_Null() {
            assertThatThrownBy(() -> PortCode.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("PortCode value must not be null");
        }

        @Test
        @DisplayName("should throw exception when value is blank")
        void should_ThrowException_When_Blank() {
            assertThatThrownBy(() -> PortCode.of("     "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw exception when value is empty string")
        void should_ThrowException_When_Empty() {
            assertThatThrownBy(() -> PortCode.of(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PortCode value must not be blank");
        }

        @ParameterizedTest(name = "''{0}'' is too short ({0}.length < 5)")
        @ValueSource(strings = {"DE", "DEH", "DEHA"})
        @DisplayName("should throw exception when code is too short")
        void should_ThrowException_When_TooShort(String shortCode) {
            assertThatThrownBy(() -> PortCode.of(shortCode))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PortCode must be exactly 5 characters");
        }

        @ParameterizedTest(name = "''{0}'' is too long ({0}.length > 5)")
        @ValueSource(strings = {"DEHAMA", "DEHAMBURG", "ABCDEFGH"})
        @DisplayName("should throw exception when code is too long")
        void should_ThrowException_When_TooLong(String longCode) {
            assertThatThrownBy(() -> PortCode.of(longCode))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PortCode must be exactly 5 characters");
        }
    }

    // ==================== Equality Tests ====================

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when values match")
        void should_BeEqual_When_SameValue() {
            PortCode a = PortCode.of("DEHAM");
            PortCode b = PortCode.of("DEHAM");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("should be equal regardless of input case")
        void should_BeEqual_When_SameValueDifferentCase() {
            PortCode upper = PortCode.of("DEHAM");
            PortCode lower = PortCode.of("deham");

            assertThat(upper).isEqualTo(lower);
        }

        @Test
        @DisplayName("should not be equal when values differ")
        void should_NotBeEqual_When_DifferentValues() {
            PortCode a = PortCode.of("DEHAM");
            PortCode b = PortCode.of("CNSHA");

            assertThat(a).isNotEqualTo(b);
        }
    }

    // ==================== ToString Tests ====================

    @Nested
    @DisplayName("String Representation")
    class StringRepresentation {

        @Test
        @DisplayName("should return the port code value")
        void should_ReturnValue_When_ToStringCalled() {
            PortCode portCode = PortCode.of("USLAX");

            assertThat(portCode.toString()).isEqualTo("USLAX");
        }
    }
}
