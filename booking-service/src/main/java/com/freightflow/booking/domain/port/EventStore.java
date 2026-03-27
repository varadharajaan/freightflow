package com.freightflow.booking.domain.port;

import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.commons.domain.BookingId;

import java.util.List;

/**
 * Outbound port for the event store — persists domain events as the source of truth.
 *
 * <p>In Event Sourcing, the event store replaces the traditional entity table as
 * the primary storage mechanism. The current state of an aggregate is derived by
 * replaying its event history.</p>
 *
 * <h3>Guarantees</h3>
 * <ul>
 *   <li><b>Append-only</b> — events are immutable once stored; never updated or deleted</li>
 *   <li><b>Ordered</b> — events for an aggregate are stored and returned in version order</li>
 *   <li><b>Optimistic concurrency</b> — duplicate versions are rejected (aggregate_id + version is unique)</li>
 * </ul>
 *
 * @see BookingEvent
 */
public interface EventStore {

    /**
     * Appends domain events to the event store.
     *
     * <p>All events in the list are persisted atomically within a single transaction.
     * If the expected version conflicts (another transaction already appended events),
     * an optimistic concurrency exception is thrown.</p>
     *
     * @param aggregateId the booking aggregate ID
     * @param events      the domain events to append (in order)
     * @param expectedVersion the expected current version of the aggregate (for optimistic locking)
     * @throws com.freightflow.commons.exception.ConflictException if version conflict detected
     */
    void append(BookingId aggregateId, List<BookingEvent> events, long expectedVersion);

    /**
     * Loads all events for an aggregate in version order.
     *
     * <p>Used to reconstruct the aggregate state by replaying events from the beginning.</p>
     *
     * @param aggregateId the booking aggregate ID
     * @return ordered list of domain events (may be empty if aggregate doesn't exist)
     */
    List<BookingEvent> loadEvents(BookingId aggregateId);

    /**
     * Loads events for an aggregate starting from a specific version.
     *
     * <p>Used in combination with snapshots — load snapshot, then replay events
     * from the snapshot version onward.</p>
     *
     * @param aggregateId  the booking aggregate ID
     * @param fromVersion  the version to start loading from (inclusive)
     * @return ordered list of domain events from the specified version
     */
    List<BookingEvent> loadEventsFromVersion(BookingId aggregateId, long fromVersion);
}
