package com.freightflow.booking.application.query;

import com.freightflow.commons.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * CQRS Query Handler — handles all <b>read</b> operations for the booking domain.
 *
 * <p>This is the <b>read side</b> of CQRS. It queries denormalized projections
 * ({@link BookingView}) that are materialized from domain events by the
 * {@link com.freightflow.booking.infrastructure.adapter.out.persistence.projection.BookingProjectionUpdater}.
 * The read model is optimized for query performance — flat, pre-joined, and
 * requires no complex aggregate reconstruction.</p>
 *
 * <h3>Separation from Write Side</h3>
 * <p>The write side ({@link com.freightflow.booking.application.command.BookingCommandHandler})
 * handles commands and produces events. This query handler consumes the projections
 * built from those events. The two sides can be scaled independently and may even
 * use different data stores in the future.</p>
 *
 * <h3>Event History</h3>
 * <p>In addition to projection queries, this handler supports loading the full event
 * history of a booking via {@link #getBookingHistory(String)}, which reads directly
 * from the event store. This is useful for audit trails and debugging.</p>
 *
 * @see BookingView
 * @see BookingProjectionRepository
 * @see com.freightflow.booking.application.command.BookingCommandHandler
 */
@Service
public class BookingQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingQueryHandler.class);

    private final BookingProjectionRepository projectionRepository;
    private final BookingEventStoreQueryPort eventStoreQueryPort;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param projectionRepository the read model repository for booking projections
     * @param eventStoreQueryPort  the port for loading event history from the event store
     */
    public BookingQueryHandler(BookingProjectionRepository projectionRepository,
                               BookingEventStoreQueryPort eventStoreQueryPort) {
        this.projectionRepository = Objects.requireNonNull(projectionRepository,
                "projectionRepository must not be null");
        this.eventStoreQueryPort = Objects.requireNonNull(eventStoreQueryPort,
                "eventStoreQueryPort must not be null");
    }

    /**
     * Retrieves a single booking projection by its ID.
     *
     * @param bookingId the booking aggregate ID
     * @return the booking view projection
     * @throws ResourceNotFoundException if no booking projection exists for the given ID
     */
    @Transactional(readOnly = true)
    public BookingView getBooking(String bookingId) {
        log.debug("Querying booking projection: bookingId={}", bookingId);

        return projectionRepository.findById(bookingId)
                .orElseThrow(() -> {
                    log.warn("Booking projection not found: bookingId={}", bookingId);
                    return ResourceNotFoundException.forBooking(bookingId);
                });
    }

    /**
     * Retrieves all booking projections for a given customer.
     *
     * @param customerId the customer ID
     * @return list of booking views for the customer (may be empty, never null)
     */
    @Transactional(readOnly = true)
    public List<BookingView> getBookingsByCustomer(String customerId) {
        log.debug("Querying bookings by customer: customerId={}", customerId);

        List<BookingView> bookings = projectionRepository.findByCustomerId(customerId);

        log.debug("Found {} booking(s) for customer: customerId={}", bookings.size(), customerId);
        return bookings;
    }

    /**
     * Loads the full event history for a booking from the event store.
     *
     * <p>Returns the complete timeline of domain events that have been applied to
     * the booking aggregate, in version order. Useful for audit trails, debugging,
     * and understanding the full lifecycle of a booking.</p>
     *
     * @param bookingId the booking aggregate ID
     * @return ordered list of event records (may be empty if booking has no events)
     */
    @Transactional(readOnly = true)
    public List<BookingEventRecord> getBookingHistory(String bookingId) {
        log.debug("Loading booking event history: bookingId={}", bookingId);

        List<BookingEventRecord> history = eventStoreQueryPort.loadEventHistory(bookingId);

        if (history.isEmpty()) {
            log.warn("No event history found for booking: bookingId={}", bookingId);
        } else {
            log.debug("Loaded {} event(s) for booking: bookingId={}", history.size(), bookingId);
        }

        return history;
    }

    /**
     * Query port for loading event history from the event store.
     *
     * <p>This inner interface decouples the query handler from the event store
     * infrastructure. The JPA event store adapter implements this port.</p>
     */
    public interface BookingEventStoreQueryPort {

        /**
         * Loads all events for a booking as displayable event records.
         *
         * @param bookingId the booking aggregate ID
         * @return ordered list of event records
         */
        List<BookingEventRecord> loadEventHistory(String bookingId);
    }
}
