package com.freightflow.booking.infrastructure.adapter.out.persistence.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link BookingProjectionEntity}.
 *
 * <p>Provides data access for the {@code booking_projections} table. Methods follow
 * Spring Data naming conventions for auto-generated queries optimized for the
 * read model's access patterns.</p>
 *
 * @see BookingProjectionEntity
 * @see JpaBookingProjectionAdapter
 */
public interface SpringDataProjectionRepository extends JpaRepository<BookingProjectionEntity, UUID> {

    /**
     * Finds all booking projections for a customer, ordered by sequence number descending
     * (most recent first).
     *
     * @param customerId the customer ID
     * @return list of projection entities for the customer
     */
    List<BookingProjectionEntity> findByCustomerIdOrderBySequenceNumberDesc(UUID customerId);
}
