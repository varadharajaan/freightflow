package com.freightflow.commons.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Value Object representing a monetary amount with its currency.
 *
 * <p>Follows the Value Object pattern from Domain-Driven Design — instances are immutable,
 * compared by value, and self-validating. A {@code Money} instance always represents a
 * non-negative amount paired with a valid {@link Currency}.</p>
 *
 * <p>Arithmetic operations enforce currency consistency: you cannot add USD to EUR.
 * This prevents subtle financial bugs at compile time rather than at runtime in production.</p>
 *
 * @param amount   the monetary amount (must be {@code >= 0})
 * @param currency the ISO 4217 currency (must not be {@code null})
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "Money amount must not be null");
        Objects.requireNonNull(currency, "Money currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount must not be negative, got: " + amount);
        }
    }

    /**
     * Creates a {@code Money} instance from a {@link BigDecimal} amount and an ISO 4217 currency code.
     *
     * @param amount       the monetary amount
     * @param currencyCode the ISO 4217 currency code (e.g. "USD", "EUR", "CNY")
     * @return a new {@code Money} instance
     * @throws IllegalArgumentException if the amount is negative or the currency code is invalid
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /**
     * Creates a zero-amount {@code Money} instance for the given currency.
     *
     * @param currencyCode the ISO 4217 currency code (e.g. "USD", "EUR", "CNY")
     * @return a new {@code Money} instance with amount {@code 0}
     * @throws IllegalArgumentException if the currency code is invalid
     */
    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, Currency.getInstance(currencyCode));
    }

    /**
     * Adds another {@code Money} to this one, returning a new instance.
     *
     * <p>Both operands must share the same currency.</p>
     *
     * @param other the {@code Money} to add
     * @return a new {@code Money} representing the sum
     * @throws IllegalArgumentException if the currencies do not match
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtracts another {@code Money} from this one, returning a new instance.
     *
     * <p>Both operands must share the same currency. The result must not be negative.</p>
     *
     * @param other the {@code Money} to subtract
     * @return a new {@code Money} representing the difference
     * @throws IllegalArgumentException if the currencies do not match or the result would be negative
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * Validates that both {@code Money} instances use the same currency.
     *
     * @param other the other {@code Money} to compare currency with
     * @throws IllegalArgumentException if currencies differ
     */
    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "Cannot perform arithmetic with null Money");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot combine %s with %s".formatted(this.currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return "%s %s".formatted(amount.toPlainString(), currency.getCurrencyCode());
    }
}
