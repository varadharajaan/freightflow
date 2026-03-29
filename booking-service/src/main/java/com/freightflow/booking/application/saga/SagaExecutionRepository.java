package com.freightflow.booking.application.saga;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting saga execution state.
 *
 * <p>Abstracts the saga persistence mechanism from the application layer.
 * The infrastructure layer provides the implementation (JPA adapter).
 * This follows the Dependency Inversion Principle: the application defines
 * the contract, the infrastructure implements it.</p>
 *
 * @see SagaExecution
 * @see com.freightflow.booking.infrastructure.adapter.out.persistence.saga.JpaSagaExecutionAdapter
 */
public interface SagaExecutionRepository {

    /**
     * Persists a saga execution (create or update).
     *
     * @param sagaExecution the saga execution to persist
     * @return the persisted saga execution
     */
    SagaExecution save(SagaExecution sagaExecution);

    /**
     * Finds a saga execution by its unique identifier.
     *
     * @param sagaId the saga execution ID
     * @return the saga execution, or empty if not found
     */
    Optional<SagaExecution> findById(UUID sagaId);

    /**
     * Finds a saga execution by its idempotency key.
     *
     * <p>Used by the orchestrator to detect duplicate requests. If a saga with
     * the same idempotency key already exists, the existing result is returned
     * instead of creating a new saga execution.</p>
     *
     * @param idempotencyKey the caller-provided idempotency key
     * @return the saga execution, or empty if no saga exists with this key
     */
    Optional<SagaExecution> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds all saga executions for a given booking.
     *
     * <p>A booking may have multiple saga executions if previous attempts failed
     * and were retried with different idempotency keys.</p>
     *
     * @param bookingId the booking ID
     * @return list of saga executions (may be empty)
     */
    List<SagaExecution> findByBookingId(String bookingId);

    /**
     * Finds saga executions by lifecycle status.
     *
     * @param status saga status
     * @return matching saga executions (may be empty)
     */
    List<SagaExecution> findByStatus(SagaStatus status);
}
