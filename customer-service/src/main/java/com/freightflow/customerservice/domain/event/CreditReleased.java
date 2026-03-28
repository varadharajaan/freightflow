package com.freightflow.customerservice.domain.event;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when previously allocated credit is released.
 *
 * <p>Typically triggered when a booking is cancelled or a payment is received.
 * Consumers: billing-service (update ledger), notification-service (credit release alert).</p>
 *
 * @param eventId          unique event identifier
 * @param customerId       the customer whose credit was released
 * @param amount           the amount of credit released
 * @param currentCreditUsed the total credit now in use after release
 * @param creditLimit      the customer's total credit limit
 * @param occurredAt       when the release happened
 */
public record CreditReleased(
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
    public CreditReleased(CustomerId customerId, Money amount, Money currentCreditUsed,
                           Money creditLimit, Instant occurredAt) {
        this(UUID.randomUUID(), customerId, amount, currentCreditUsed, creditLimit, occurredAt);
    }
}
