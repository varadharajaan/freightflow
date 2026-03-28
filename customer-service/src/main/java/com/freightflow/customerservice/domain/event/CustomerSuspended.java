package com.freightflow.customerservice.domain.event;

import com.freightflow.commons.domain.CustomerId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a customer account is suspended.
 *
 * <p>Consumers: notification-service (suspension notice email),
 * booking-service (block new bookings for this customer).</p>
 *
 * @param eventId    unique event identifier
 * @param customerId the suspended customer
 * @param reason     the suspension reason
 * @param occurredAt when the suspension happened
 */
public record CustomerSuspended(
        UUID eventId,
        CustomerId customerId,
        String reason,
        Instant occurredAt
) implements CustomerEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public CustomerSuspended(CustomerId customerId, String reason, Instant occurredAt) {
        this(UUID.randomUUID(), customerId, reason, occurredAt);
    }
}
