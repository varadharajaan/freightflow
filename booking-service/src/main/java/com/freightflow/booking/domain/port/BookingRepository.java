package com.freightflow.booking.domain.port;

import com.freightflow.booking.domain.model.Booking;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for booking persistence.
 *
 * <p>This interface defines the contract that the domain layer expects from
 * the persistence layer (Dependency Inversion Principle). The domain does NOT
 * depend on JPA, Hibernate, or any infrastructure technology.</p>
 *
 * <p>The implementation (JPA adapter) lives in the infrastructure layer
 * and adapts this port to Spring Data JPA.</p>
 *
 * @see com.freightflow.booking.infrastructure.adapter.out.persistence
 */
public interface BookingRepository {

    /**
     * Persists a new or updated booking.
     *
     * @param booking the booking aggregate to save
     * @return the saved booking (with updated version)
     */
    Booking save(Booking booking);

    /**
     * Finds a booking by its ID.
     *
     * @param bookingId the booking identifier
     * @return the booking, or empty if not found
     */
    Optional<Booking> findById(BookingId bookingId);

    /**
     * Finds all bookings for a customer.
     *
     * @param customerId the customer identifier
     * @return list of bookings (may be empty)
     */
    List<Booking> findByCustomerId(CustomerId customerId);

    /**
     * Checks whether a booking exists.
     *
     * @param bookingId the booking identifier
     * @return true if the booking exists
     */
    boolean existsById(BookingId bookingId);
}
