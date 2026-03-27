package com.freightflow.booking.infrastructure.adapter.out.persistence.repository;

import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for booking entities.
 *
 * <p>This is a <b>technology-specific</b> interface that extends Spring Data's
 * {@link JpaRepository} and {@link JpaSpecificationExecutor}. It is NOT the domain port —
 * the domain port is {@link com.freightflow.booking.domain.port.BookingRepository}.</p>
 *
 * <p>The {@link JpaBookingPersistenceAdapter} wraps this repository and maps between
 * JPA entities and domain objects.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>{@link JpaRepository} — standard CRUD + pagination + sorting</li>
 *   <li>{@link JpaSpecificationExecutor} — dynamic queries via JPA Specifications</li>
 *   <li>Custom JPQL queries for optimized data access</li>
 * </ul>
 */
@Repository
public interface SpringDataBookingRepository
        extends JpaRepository<BookingJpaEntity, UUID>, JpaSpecificationExecutor<BookingJpaEntity> {

    /**
     * Finds all bookings for a given customer, ordered by creation date descending.
     *
     * @param customerId the customer UUID
     * @return list of booking entities
     */
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.customerId = :customerId ORDER BY b.createdAt DESC")
    List<BookingJpaEntity> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") UUID customerId);

    /**
     * Finds bookings by status.
     *
     * @param status the booking status string
     * @return list of booking entities
     */
    List<BookingJpaEntity> findByStatus(String status);

    /**
     * Counts bookings for a customer in a specific status.
     *
     * @param customerId the customer UUID
     * @param status     the booking status
     * @return the count
     */
    @Query("SELECT COUNT(b) FROM BookingJpaEntity b WHERE b.customerId = :customerId AND b.status = :status")
    long countByCustomerIdAndStatus(@Param("customerId") UUID customerId, @Param("status") String status);
}
