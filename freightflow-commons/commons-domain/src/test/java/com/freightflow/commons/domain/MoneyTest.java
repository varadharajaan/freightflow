package com.freightflow.commons.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Money} value object.
 *
 * <p>Tests cover creation, arithmetic operations (add, subtract), zero factory,
 * currency mismatch enforcement, and negative amount rejection.</p>
 *
 * @see Money
 */
@DisplayName("Money Value Object")
class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    // ==================== Creation Tests ====================

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create Money with valid amount and currency")
        void should_CreateMoney_When_ValidAmountAndCurrency() {
            // When
            Money money = new Money(new BigDecimal("100.50"), USD);

            // Then
            assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("100.50"));
            assertThat(money.currency()).isEqualTo(USD);
        }

        @Test
        @DisplayName("should create Money via static factory with currency code")
        void should_CreateMoney_When_UsingStaticFactory() {
            // When
            Money money = Money.of(new BigDecimal("250.00"), "EUR");

            // Then
            assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("250.00"));
            assertThat(money.currency()).isEqualTo(EUR);
        }

        @Test
        @DisplayName("should create zero Money for given currency")
        void should_CreateZeroMoney_When_UsingZeroFactory() {
            // When
            Money zero = Money.zero("USD");

            // Then
            assertThat(zero.amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(zero.currency()).isEqualTo(USD);
        }

        @Test
        @DisplayName("should allow zero amount")
        void should_AllowZeroAmount_When_CreatingMoney() {
            // When
            Money money = new Money(BigDecimal.ZERO, USD);

            // Then
            assertThat(money.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should throw exception when amount is null")
        void should_ThrowException_When_AmountNull() {
            assertThatThrownBy(() -> new Money(null, USD))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Money amount must not be null");
        }

        @Test
        @DisplayName("should throw exception when currency is null")
        void should_ThrowException_When_CurrencyNull() {
            assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Money currency must not be null");
        }

        @ParameterizedTest(name = "amount={0} should be rejected")
        @MethodSource("negativeAmounts")
        @DisplayName("should reject negative amounts")
        void should_ThrowException_When_AmountNegative(BigDecimal negativeAmount) {
            assertThatThrownBy(() -> new Money(negativeAmount, USD))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Money amount must not be negative");
        }

        static Stream<BigDecimal> negativeAmounts() {
            return Stream.of(
                    new BigDecimal("-0.01"),
                    new BigDecimal("-1"),
                    new BigDecimal("-9999.99")
            );
        }
    }

    // ==================== Addition Tests ====================

    @Nested
    @DisplayName("Addition")
    class Addition {

        @Test
        @DisplayName("should add two Money values with same currency")
        void should_AddCorrectly_When_SameCurrency() {
            // Given
            Money a = Money.of(new BigDecimal("100.00"), "USD");
            Money b = Money.of(new BigDecimal("50.25"), "USD");

            // When
            Money result = a.add(b);

            // Then
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("150.25"));
            assertThat(result.currency()).isEqualTo(USD);
        }

        @Test
        @DisplayName("should add zero without changing value")
        void should_ReturnSameValue_When_AddingZero() {
            // Given
            Money money = Money.of(new BigDecimal("42.00"), "USD");
            Money zero = Money.zero("USD");

            // When
            Money result = money.add(zero);

            // Then
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("42.00"));
        }

        @Test
        @DisplayName("should throw exception when adding different currencies")
        void should_ThrowException_When_AddingDifferentCurrencies() {
            // Given
            Money usd = Money.of(new BigDecimal("100"), "USD");
            Money eur = Money.of(new BigDecimal("50"), "EUR");

            // When / Then
            assertThatThrownBy(() -> usd.add(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }
    }

    // ==================== Subtraction Tests ====================

    @Nested
    @DisplayName("Subtraction")
    class Subtraction {

        @Test
        @DisplayName("should subtract two Money values with same currency")
        void should_SubtractCorrectly_When_SameCurrency() {
            // Given
            Money a = Money.of(new BigDecimal("100.00"), "USD");
            Money b = Money.of(new BigDecimal("30.50"), "USD");

            // When
            Money result = a.subtract(b);

            // Then
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("69.50"));
            assertThat(result.currency()).isEqualTo(USD);
        }

        @Test
        @DisplayName("should return zero when subtracting equal amounts")
        void should_ReturnZero_When_SubtractingEqualAmounts() {
            // Given
            Money a = Money.of(new BigDecimal("75.00"), "USD");
            Money b = Money.of(new BigDecimal("75.00"), "USD");

            // When
            Money result = a.subtract(b);

            // Then
            assertThat(result.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should throw exception when result would be negative")
        void should_ThrowException_When_ResultWouldBeNegative() {
            // Given
            Money a = Money.of(new BigDecimal("10.00"), "USD");
            Money b = Money.of(new BigDecimal("20.00"), "USD");

            // When / Then
            assertThatThrownBy(() -> a.subtract(b))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Money amount must not be negative");
        }

        @Test
        @DisplayName("should throw exception when subtracting different currencies")
        void should_ThrowException_When_SubtractingDifferentCurrencies() {
            // Given
            Money usd = Money.of(new BigDecimal("100"), "USD");
            Money eur = Money.of(new BigDecimal("50"), "EUR");

            // When / Then
            assertThatThrownBy(() -> usd.subtract(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }
    }

    // ==================== ToString Tests ====================

    @Nested
    @DisplayName("String Representation")
    class StringRepresentation {

        @Test
        @DisplayName("should format as 'amount currency'")
        void should_FormatCorrectly_When_ToStringCalled() {
            Money money = Money.of(new BigDecimal("1234.56"), "USD");

            assertThat(money.toString()).isEqualTo("1234.56 USD");
        }
    }

    // ==================== Equality Tests ====================

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when amount and currency match")
        void should_BeEqual_When_SameAmountAndCurrency() {
            Money a = Money.of(new BigDecimal("100.00"), "USD");
            Money b = Money.of(new BigDecimal("100.00"), "USD");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("should not be equal when currencies differ")
        void should_NotBeEqual_When_DifferentCurrencies() {
            Money usd = Money.of(new BigDecimal("100.00"), "USD");
            Money eur = Money.of(new BigDecimal("100.00"), "EUR");

            assertThat(usd).isNotEqualTo(eur);
        }
    }
}
