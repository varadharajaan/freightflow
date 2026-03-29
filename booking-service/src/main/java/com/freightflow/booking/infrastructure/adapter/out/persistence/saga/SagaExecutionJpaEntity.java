package com.freightflow.booking.infrastructure.adapter.out.persistence.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for persisting saga execution state to PostgreSQL.
 *
 * <p>This entity lives in the infrastructure layer and is NOT exposed to the application
 * or domain layers. The application works with {@link com.freightflow.booking.application.saga.SagaExecution},
 * and this entity is mapped to/from the domain model via the
 * {@link JpaSagaExecutionAdapter}.</p>
 *
 * <p>Follows the Hexagonal Architecture separation: JPA annotations stay in
 * the infrastructure layer, the domain remains persistence-ignorant.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>JPA auditing via {@link AuditingEntityListener}</li>
 *   <li>Optimistic locking via {@link Version} to prevent lost updates</li>
 *   <li>Unique constraint on {@code idempotency_key} for duplicate detection</li>
 *   <li>Completed steps stored as comma-separated values for simplicity</li>
 * </ul>
 *
 * @see com.freightflow.booking.application.saga.SagaExecution
 * @see JpaSagaExecutionAdapter
 */
@Entity
@Table(name = "saga_executions")
@EntityListeners(AuditingEntityListener.class)
public class SagaExecutionJpaEntity {

    @Id
    @Column(name = "saga_id", nullable = false, updatable = false)
    private UUID sagaId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    @Column(name = "voyage_id", nullable = false)
    private String voyageId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "current_step", length = 30)
    private String currentStep;

    @Column(name = "completed_steps", length = 500)
    private String completedSteps;

    @Column(name = "failed_step", length = 30)
    private String failedStep;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Required by JPA. Do not use directly — use the adapter to create entities
     * from domain objects.
     */
    protected SagaExecutionJpaEntity() {
        // JPA requires a no-arg constructor
    }

    // ==================== Getters and Setters ====================

    /** Returns the saga execution unique identifier. */
    public UUID getSagaId() { return sagaId; }

    /** Sets the saga execution unique identifier. */
    public void setSagaId(UUID sagaId) { this.sagaId = sagaId; }

    /** Returns the booking ID. */
    public String getBookingId() { return bookingId; }

    /** Sets the booking ID. */
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    /** Returns the voyage ID. */
    public String getVoyageId() { return voyageId; }

    /** Sets the voyage ID. */
    public void setVoyageId(String voyageId) { this.voyageId = voyageId; }

    /** Returns the saga status as a string. */
    public String getStatus() { return status; }

    /** Sets the saga status as a string. */
    public void setStatus(String status) { this.status = status; }

    /** Returns the current step name (nullable). */
    public String getCurrentStep() { return currentStep; }

    /** Sets the current step name. */
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }

    /** Returns the completed steps as a comma-separated string. */
    public String getCompletedSteps() { return completedSteps; }

    /** Sets the completed steps as a comma-separated string. */
    public void setCompletedSteps(String completedSteps) { this.completedSteps = completedSteps; }

    /** Returns the failed step name (nullable). */
    public String getFailedStep() { return failedStep; }

    /** Sets the failed step name. */
    public void setFailedStep(String failedStep) { this.failedStep = failedStep; }

    /** Returns the failure reason (nullable). */
    public String getFailureReason() { return failureReason; }

    /** Sets the failure reason. */
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    /** Returns the idempotency key. */
    public String getIdempotencyKey() { return idempotencyKey; }

    /** Sets the idempotency key. */
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    /** Returns the saga start timestamp. */
    public Instant getStartedAt() { return startedAt; }

    /** Sets the saga start timestamp. */
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    /** Returns the saga completion timestamp (nullable). */
    public Instant getCompletedAt() { return completedAt; }

    /** Sets the saga completion timestamp. */
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    /** Returns the optimistic lock version. */
    public long getVersion() { return version; }

    /** Sets the optimistic lock version. */
    public void setVersion(long version) { this.version = version; }

    @Override
    public String toString() {
        return "SagaExecutionJpaEntity[sagaId=%s, bookingId=%s, status=%s, currentStep=%s]".formatted(
                sagaId, bookingId, status, currentStep);
    }
}
