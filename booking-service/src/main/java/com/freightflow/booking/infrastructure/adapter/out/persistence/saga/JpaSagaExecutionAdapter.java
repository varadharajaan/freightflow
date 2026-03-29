package com.freightflow.booking.infrastructure.adapter.out.persistence.saga;

import com.freightflow.booking.application.saga.SagaExecution;
import com.freightflow.booking.application.saga.SagaExecutionRepository;
import com.freightflow.booking.application.saga.SagaStatus;
import com.freightflow.booking.application.saga.SagaStep;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA persistence adapter implementing the {@link SagaExecutionRepository} port.
 *
 * <p>This is the <b>outbound adapter</b> in Hexagonal Architecture. It translates
 * between the domain model ({@link SagaExecution}) and the JPA entity
 * ({@link SagaExecutionJpaEntity}). Mapping is done inline since the saga entity
 * structure is simple enough to not warrant a separate mapper class.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts Spring Data JPA to the domain port interface</li>
 *   <li><b>Dependency Inversion</b> — application layer defines the port, this implements it</li>
 *   <li><b>Inline Mapping</b> — entity ↔ domain mapping is co-located for simplicity</li>
 *   <li><b>Comma-separated steps</b> — completed steps stored as CSV string in a single column</li>
 * </ul>
 *
 * @see SagaExecutionRepository
 * @see SpringDataSagaExecutionRepository
 * @see SagaExecutionJpaEntity
 */
@Component
public class JpaSagaExecutionAdapter implements SagaExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaSagaExecutionAdapter.class);

    private final SpringDataSagaExecutionRepository jpaRepository;

    /**
     * Creates a new adapter with the required Spring Data repository.
     *
     * @param jpaRepository the Spring Data JPA repository (must not be null)
     */
    public JpaSagaExecutionAdapter(SpringDataSagaExecutionRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "JPA repository must not be null");
    }

    @Override
    @Profiled(value = "saveSagaExecution", slowThresholdMs = 500)
    public SagaExecution save(SagaExecution sagaExecution) {
        log.debug("Persisting saga execution: sagaId={}, status={}",
                sagaExecution.getSagaId(), sagaExecution.getStatus());

        SagaExecutionJpaEntity entity = toEntity(sagaExecution);
        SagaExecutionJpaEntity saved = jpaRepository.save(entity);

        log.debug("Saga execution persisted: sagaId={}, version={}",
                saved.getSagaId(), saved.getVersion());

        return toDomain(saved);
    }

    @Override
    public Optional<SagaExecution> findById(UUID sagaId) {
        log.debug("Finding saga execution: sagaId={}", sagaId);

        return jpaRepository.findById(sagaId)
                .map(entity -> {
                    log.debug("Saga execution found: sagaId={}, status={}",
                            entity.getSagaId(), entity.getStatus());
                    return toDomain(entity);
                });
    }

    @Override
    public Optional<SagaExecution> findByIdempotencyKey(String idempotencyKey) {
        log.debug("Finding saga execution by idempotency key: key={}", idempotencyKey);

        return jpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(entity -> {
                    log.debug("Saga execution found by idempotency key: sagaId={}, key={}",
                            entity.getSagaId(), idempotencyKey);
                    return toDomain(entity);
                });
    }

    @Override
    public List<SagaExecution> findByBookingId(String bookingId) {
        log.debug("Finding saga executions for booking: bookingId={}", bookingId);

        List<SagaExecutionJpaEntity> entities = jpaRepository.findByBookingIdOrderByStartedAtDesc(bookingId);

        log.debug("Found {} saga executions for booking: bookingId={}", entities.size(), bookingId);

        return entities.stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<SagaExecution> findByStatus(SagaStatus status) {
        log.debug("Finding saga executions by status: status={}", status);
        return jpaRepository.findByStatus(status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    // ==================== Entity ↔ Domain Mapping ====================

    /**
     * Maps a domain {@link SagaExecution} to a JPA {@link SagaExecutionJpaEntity}.
     *
     * @param domain the domain saga execution
     * @return the JPA entity
     */
    private SagaExecutionJpaEntity toEntity(SagaExecution domain) {
        var entity = new SagaExecutionJpaEntity();
        entity.setSagaId(domain.getSagaId());
        entity.setBookingId(domain.getBookingId());
        entity.setVoyageId(domain.getVoyageId());
        entity.setStatus(domain.getStatus().name());
        entity.setCurrentStep(domain.getCurrentStep() != null ? domain.getCurrentStep().name() : null);
        entity.setCompletedSteps(serializeSteps(domain.getCompletedSteps()));
        entity.setFailedStep(domain.getFailedStep() != null ? domain.getFailedStep().name() : null);
        entity.setFailureReason(domain.getFailureReason());
        entity.setIdempotencyKey(domain.getIdempotencyKey());
        entity.setStartedAt(domain.getStartedAt());
        entity.setCompletedAt(domain.getCompletedAt());
        return entity;
    }

    /**
     * Maps a JPA {@link SagaExecutionJpaEntity} to a domain {@link SagaExecution}.
     *
     * @param entity the JPA entity
     * @return the domain saga execution
     */
    private SagaExecution toDomain(SagaExecutionJpaEntity entity) {
        return SagaExecution.reconstitute(
                entity.getSagaId(),
                entity.getBookingId(),
                entity.getVoyageId(),
                SagaStatus.valueOf(entity.getStatus()),
                entity.getCurrentStep() != null ? SagaStep.valueOf(entity.getCurrentStep()) : null,
                deserializeSteps(entity.getCompletedSteps()),
                entity.getFailedStep() != null ? SagaStep.valueOf(entity.getFailedStep()) : null,
                entity.getFailureReason(),
                entity.getIdempotencyKey(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    /**
     * Serializes a list of saga steps to a comma-separated string.
     *
     * @param steps the list of steps
     * @return comma-separated step names, or empty string if the list is empty
     */
    private String serializeSteps(List<SagaStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        return steps.stream()
                .map(SagaStep::name)
                .collect(Collectors.joining(","));
    }

    /**
     * Deserializes a comma-separated string of step names to a list of saga steps.
     *
     * @param csv the comma-separated step names
     * @return list of saga steps, or empty list if the CSV is blank
     */
    private List<SagaStep> deserializeSteps(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(SagaStep::valueOf)
                .toList();
    }
}
