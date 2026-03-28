package com.freightflow.customerservice.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a contractual agreement between a customer and FreightFlow.
 *
 * <p>Contracts define pricing tiers, discount percentages, and booking limits.
 * A customer may have multiple contracts, but only one active contract per type
 * at any given time.</p>
 *
 * <h3>Contract Types</h3>
 * <ul>
 *   <li><b>STANDARD</b> — pay-as-you-go pricing with no volume commitment</li>
 *   <li><b>VOLUME</b> — discounted rates for committing to a minimum booking volume</li>
 *   <li><b>ENTERPRISE</b> — custom pricing with SLA guarantees and dedicated support</li>
 * </ul>
 *
 * @see Customer
 */
public class Contract {

    private final UUID contractId;
    private final UUID customerId;
    private final ContractType contractType;
    private final double discountPercentage;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final int maxBookingsPerMonth;

    /**
     * Creates a new contract with full validation.
     *
     * @param contractId         the unique contract identifier
     * @param customerId         the customer this contract belongs to
     * @param contractType       the contract tier
     * @param discountPercentage the discount percentage (0.0 to 100.0)
     * @param validFrom          the contract start date
     * @param validTo            the contract end date
     * @param maxBookingsPerMonth the maximum bookings allowed per calendar month
     */
    public Contract(UUID contractId, UUID customerId, ContractType contractType,
                    double discountPercentage, LocalDate validFrom, LocalDate validTo,
                    int maxBookingsPerMonth) {
        this.contractId = Objects.requireNonNull(contractId, "Contract ID must not be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID must not be null");
        this.contractType = Objects.requireNonNull(contractType, "Contract type must not be null");
        this.validFrom = Objects.requireNonNull(validFrom, "Valid from date must not be null");
        this.validTo = Objects.requireNonNull(validTo, "Valid to date must not be null");

        if (discountPercentage < 0 || discountPercentage > 100) {
            throw new IllegalArgumentException(
                    "Discount percentage must be between 0 and 100, got: " + discountPercentage);
        }
        if (maxBookingsPerMonth <= 0) {
            throw new IllegalArgumentException(
                    "Max bookings per month must be positive, got: " + maxBookingsPerMonth);
        }
        if (!validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException(
                    "Valid to date must be after valid from date: %s → %s".formatted(validFrom, validTo));
        }

        this.discountPercentage = discountPercentage;
        this.maxBookingsPerMonth = maxBookingsPerMonth;
    }

    // ==================== Business Methods ====================

    /**
     * Checks whether this contract is currently active (valid date range includes today).
     *
     * @return true if the contract is active today
     */
    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(validFrom) && !today.isAfter(validTo);
    }

    /**
     * Checks whether this contract has expired (valid to date is in the past).
     *
     * @return true if the contract has expired
     */
    public boolean isExpired() {
        return LocalDate.now().isAfter(validTo);
    }

    // ==================== Contract Type Enum ====================

    /**
     * Defines the tier of a customer contract.
     */
    public enum ContractType {
        /** Pay-as-you-go pricing with no volume commitment. */
        STANDARD,
        /** Discounted rates for volume commitments. */
        VOLUME,
        /** Custom pricing with SLA guarantees and dedicated support. */
        ENTERPRISE
    }

    // ==================== Accessors ====================

    public UUID getContractId() { return contractId; }
    public UUID getCustomerId() { return customerId; }
    public ContractType getContractType() { return contractType; }
    public double getDiscountPercentage() { return discountPercentage; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public int getMaxBookingsPerMonth() { return maxBookingsPerMonth; }

    @Override
    public String toString() {
        return "Contract[id=%s, customer=%s, type=%s, discount=%.1f%%, valid=%s→%s]".formatted(
                contractId, customerId, contractType, discountPercentage, validFrom, validTo);
    }
}
