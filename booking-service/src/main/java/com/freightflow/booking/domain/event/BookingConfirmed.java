package com.freightflow.booking.domain.event;

import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.VoyageId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a booking transitions from DRAFT to CONFIRMED.
 *
 * <p>Consumers: billing-service (generate invoice), tracking-service (start tracking).</p>
 *
 * @param eventId     unique event identifier
 * @param bookingId   the confirmed booking
 * @param customerId  the customer
 * @param voyageId    the assigned voyage
 * @param occurredAt  when the confirmation happened
 */
public record BookingConfirmed(
        UUID eventId,
        BookingId bookingId,
        CustomerId customerId,
        VoyageId voyageId,
        Instant occurredAt
) implements BookingEvent {

    public BookingConfirmed(BookingId bookingId, CustomerId customerId,
                            VoyageId voyageId, Instant occurredAt) {
        this(UUID.randomUUID(), bookingId, customerId, voyageId, occurredAt);
    }
}
