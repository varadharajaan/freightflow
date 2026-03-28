package com.freightflow.billingservice.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representing a monetary amount with its currency.
 *
 * <p>Immutable record following DDD value object semantics. All financial calculations
 * should use this type to prevent currency mismatch errors.</p>
 *
 * @param amount   the monetary amount (must not be negative)
 * @param currency the ISO 4217 currency code (e.g., USD, EUR)
 */
public record Money(
        BigDecimal amount,
        String currency
) {

    /** Default currency for FreightFlow invoicing. */
    public static final String DEFAULT_CURRENCY = "USD";

    /**
     * Creates a validated Money instance.
     *
     * @throws IllegalArgumentException if amount is negative or currency is blank
     */
    public Money {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative, got: " + amount);
        }
        if (currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
    }

    /**
     * Factory method for creating a USD amount.
     *
     * @param amount the monetary amount
     * @return a Money instance in USD
     */
    public static Money usd(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    /**
     * Factory method for creating a zero amount.
     *
     * @param currency the currency code
     * @return a zero Money instance
     */
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Adds another Money amount (must be same currency).
     *
     * @param other the amount to add
     * @return the sum
     * @throws IllegalArgumentException if currencies differ
     */
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: %s vs %s".formatted(this.currency, other.currency));
        }
    }
}
