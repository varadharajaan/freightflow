package com.freightflow.booking.infrastructure.adapter.out.persistence.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link SagaExecutionJpaEntity}.
 *
 * <p>Provides standard CRUD operations plus custom finder methods derived from
 * Spring Data method naming conventions. This is a low-level persistence interface
 * used by the {@link JpaSagaExecutionAdapter} — it is not exposed beyond the
 * infrastructure layer.</p>
 *
 * @see SagaExecutionJpaEntity
 * @see JpaSagaExecutionAdapter
 */
@Repository
public interface SpringDataSagaExecutionRepository extends JpaRepository<SagaExecutionJpaEntity, UUID> {

    /**
     * Finds a saga execution by its idempotency key.
     *
     * <p>Used for duplicate detection — the idempotency key has a UNIQUE constraint
     * in the database, so at most one result will be returned.</p>
     *
     * @param idempotencyKey the idempotency key
     * @return the saga execution, or empty if not found
     */
    Optional<SagaExecutionJpaEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds all saga executions for a given booking, ordered by start time descending.
     *
     * @param bookingId the booking ID
     * @return list of saga executions (may be empty)
     */
    List<SagaExecutionJpaEntity> findByBookingIdOrderByStartedAtDesc(String bookingId);

    /**
     * Finds all saga executions in a given status.
     *
     * <p>Useful for monitoring and administrative queries (e.g., finding
     * all stuck sagas in COMPENSATING status).</p>
     *
     * @param status the saga status
     * @return list of saga executions in the specified status
     */
    List<SagaExecutionJpaEntity> findByStatus(String status);
}
