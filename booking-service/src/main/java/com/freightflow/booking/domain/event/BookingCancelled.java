package com.freightflow.booking.domain.event;

import com.freightflow.booking.domain.model.BookingStatus;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a booking is cancelled.
 *
 * <p>Consumers: billing-service (issue credit note/refund), notification-service
 * (cancellation email). The previous status is included so consumers can determine
 * whether compensation is needed (e.g., refund only if booking was CONFIRMED).</p>
 *
 * @param eventId        unique event identifier
 * @param bookingId      the cancelled booking
 * @param customerId     the customer
 * @param previousStatus the status before cancellation (DRAFT or CONFIRMED)
 * @param reason         the cancellation reason
 * @param occurredAt     when the cancellation happened
 */
public record BookingCancelled(
        UUID eventId,
        BookingId bookingId,
        CustomerId customerId,
        BookingStatus previousStatus,
        String reason,
        Instant occurredAt
) implements BookingEvent {

    public BookingCancelled(BookingId bookingId, CustomerId customerId,
                            BookingStatus previousStatus, String reason,
                            Instant occurredAt) {
        this(UUID.randomUUID(), bookingId, customerId, previousStatus, reason, occurredAt);
    }
}
