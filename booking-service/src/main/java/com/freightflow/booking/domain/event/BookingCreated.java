package com.freightflow.booking.domain.event;

import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.PortCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event emitted when a new booking is created in DRAFT status.
 *
 * <p>Carries all data downstream consumers need (Event-Carried State Transfer).
 * Consumers: billing-service (prepare invoice), notification-service (confirmation email).</p>
 *
 * @param eventId                 unique event identifier
 * @param bookingId               the newly created booking
 * @param customerId              the customer who placed the booking
 * @param origin                  origin port code
 * @param destination             destination port code
 * @param containerType           type of container requested
 * @param containerCount          number of containers
 * @param requestedDepartureDate  desired departure date
 * @param occurredAt              when the booking was created
 */
public record BookingCreated(
        UUID eventId,
        BookingId bookingId,
        CustomerId customerId,
        PortCode origin,
        PortCode destination,
        ContainerType containerType,
        int containerCount,
        LocalDate requestedDepartureDate,
        Instant occurredAt
) implements BookingEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public BookingCreated(BookingId bookingId, CustomerId customerId,
                          PortCode origin, PortCode destination,
                          ContainerType containerType, int containerCount,
                          LocalDate requestedDepartureDate, Instant occurredAt) {
        this(UUID.randomUUID(), bookingId, customerId, origin, destination,
                containerType, containerCount, requestedDepartureDate, occurredAt);
    }
}
