package com.freightflow.booking.application.query;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record representing a single event in a booking's event history timeline.
 *
 * <p>Used by {@link BookingQueryHandler#getBookingHistory(String)} to present the full
 * event-sourced history of a booking aggregate. Each record corresponds to one persisted
 * domain event, with the payload exposed as a generic key-value map for flexible display.</p>
 *
 * <p>This is a <b>query-only</b> data transfer object — it carries no domain logic.</p>
 *
 * @param eventId    unique identifier of the event
 * @param eventType  the event type name (e.g., "BookingCreated", "BookingConfirmed")
 * @param occurredAt when the event occurred in the domain
 * @param version    the aggregate version at which this event was applied
 * @param eventData  the event payload as a key-value map
 */
public record BookingEventRecord(
        String eventId,
        String eventType,
        Instant occurredAt,
        long version,
        Map<String, Object> eventData
) {
}
