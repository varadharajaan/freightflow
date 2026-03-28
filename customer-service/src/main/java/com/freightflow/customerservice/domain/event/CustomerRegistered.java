package com.freightflow.customerservice.domain.event;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.Money;
import com.freightflow.customerservice.domain.model.CustomerType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new customer is registered with ACTIVE status.
 *
 * <p>Carries all data downstream consumers need (Event-Carried State Transfer).
 * Consumers: notification-service (welcome email), billing-service (setup billing account).</p>
 *
 * @param eventId      unique event identifier
 * @param customerId   the newly registered customer
 * @param companyName  the company name
 * @param email        the contact email
 * @param customerType the customer type (SHIPPER, CONSIGNEE, FREIGHT_FORWARDER)
 * @param creditLimit  the initial credit limit
 * @param occurredAt   when the registration happened
 */
public record CustomerRegistered(
        UUID eventId,
        CustomerId customerId,
        String companyName,
        String email,
        CustomerType customerType,
        Money creditLimit,
        Instant occurredAt
) implements CustomerEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public CustomerRegistered(CustomerId customerId, String companyName, String email,
                               CustomerType customerType, Money creditLimit, Instant occurredAt) {
        this(UUID.randomUUID(), customerId, companyName, email, customerType, creditLimit, occurredAt);
    }
}
