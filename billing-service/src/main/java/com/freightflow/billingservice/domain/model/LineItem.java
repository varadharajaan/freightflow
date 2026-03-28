package com.freightflow.billingservice.domain.model;

import java.util.Objects;

/**
 * Value object representing a single line item on an invoice.
 *
 * <p>Immutable record following DDD value object semantics. Each line item
 * represents a charge for a specific service or fee.</p>
 *
 * @param description the description of the charge
 * @param quantity    the number of units
 * @param unitPrice   the price per unit
 * @param totalPrice  the total price (quantity × unitPrice)
 */
public record LineItem(
        String description,
        int quantity,
        Money unitPrice,
        Money totalPrice
) {

    /**
     * Creates a validated LineItem with auto-calculated total.
     *
     * @throws IllegalArgumentException if quantity is not positive
     */
    public LineItem {
        Objects.requireNonNull(description, "Description must not be null");
        Objects.requireNonNull(unitPrice, "Unit price must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
        }
        if (totalPrice == null) {
            totalPrice = new Money(
                    unitPrice.amount().multiply(java.math.BigDecimal.valueOf(quantity)),
                    unitPrice.currency()
            );
        }
    }

    /**
     * Factory method for creating a line item with auto-calculated total.
     *
     * @param description the charge description
     * @param quantity    the number of units
     * @param unitPrice   the price per unit
     * @return a new LineItem with calculated total
     */
    public static LineItem of(String description, int quantity, Money unitPrice) {
        return new LineItem(description, quantity, unitPrice, null);
    }
}
