package com.freightflow.booking.application.query;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the booking read model (projection) repository.
 *
 * <p>This is the <b>query-side</b> repository in CQRS. It provides read-only access to
 * the denormalized {@link BookingView} projections that are materialized from domain events.
 * Unlike the write-side {@code BookingRepository}, this port returns flat, query-optimized
 * views rather than domain aggregates.</p>
 *
 * <h3>Dependency Inversion</h3>
 * <p>The application layer defines this interface (port); the infrastructure layer provides
 * the JPA-based implementation (adapter). This keeps the application layer free of
 * persistence technology concerns.</p>
 *
 * @see BookingView
 * @see BookingQueryHandler
 */
public interface BookingProjectionRepository {

    /**
     * Finds a booking projection by its booking ID.
     *
     * @param bookingId the booking aggregate ID
     * @return an {@link Optional} containing the booking view, or empty if not found
     */
    Optional<BookingView> findById(String bookingId);

    /**
     * Finds all booking projections for a given customer, ordered by most recent first.
     *
     * @param customerId the customer ID
     * @return list of booking views for the customer (may be empty)
     */
    List<BookingView> findByCustomerId(String customerId);
}
