package com.freightflow.customerservice.domain.event;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when credit is allocated against a customer's credit limit.
 *
 * <p>Typically triggered when a booking is confirmed and credit is reserved.
 * Consumers: billing-service (update ledger), notification-service (credit alert).</p>
 *
 * @param eventId          unique event identifier
 * @param customerId       the customer whose credit was allocated
 * @param amount           the amount of credit allocated
 * @param currentCreditUsed the total credit now in use after allocation
 * @param creditLimit      the customer's total credit limit
 * @param occurredAt       when the allocation happened
 */
public record CreditAllocated(
        UUID eventId,
        CustomerId customerId,
        Money amount,
        Money currentCreditUsed,
        Money creditLimit,
        Instant occurredAt
) implements CustomerEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public CreditAllocated(CustomerId customerId, Money amount, Money currentCreditUsed,
                            Money creditLimit, Instant occurredAt) {
        this(UUID.randomUUID(), customerId, amount, currentCreditUsed, creditLimit, occurredAt);
    }
}
