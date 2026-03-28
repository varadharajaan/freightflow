package com.freightflow.trackingservice.infrastructure.adapter.out.persistence;

import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.trackingservice.domain.model.Container;
import com.freightflow.trackingservice.domain.port.ContainerRepository;
import com.freightflow.trackingservice.infrastructure.adapter.out.persistence.entity.ContainerJpaEntity;
import com.freightflow.trackingservice.infrastructure.adapter.out.persistence.repository.SpringDataContainerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter implementing the domain's {@link ContainerRepository} port.
 *
 * <p>This is the <b>outbound adapter</b> in Hexagonal Architecture. It translates
 * between the domain model ({@link Container}) and the JPA entity ({@link ContainerJpaEntity})
 * using the {@link ContainerEntityMapper}.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts Spring Data JPA to the domain port interface</li>
 *   <li><b>Dependency Inversion</b> — domain defines the port, infrastructure implements it</li>
 *   <li><b>Mapper isolation</b> — JPA entities never leak into the domain layer</li>
 *   <li><b>Logging</b> — DEBUG for operations, WARN for edge cases</li>
 * </ul>
 *
 * @see ContainerRepository
 * @see SpringDataContainerRepository
 * @see ContainerEntityMapper
 */
@Component
public class JpaContainerPersistenceAdapter implements ContainerRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaContainerPersistenceAdapter.class);

    private final SpringDataContainerRepository jpaRepository;
    private final ContainerEntityMapper mapper;

    /**
     * Constructor injection — depends on Spring Data repository and entity mapper.
     *
     * @param jpaRepository the Spring Data JPA repository for container entities
     * @param mapper        the entity mapper for domain-to-JPA translation
     */
    public JpaContainerPersistenceAdapter(SpringDataContainerRepository jpaRepository,
                                           ContainerEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "JPA repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the domain container to a JPA entity, persists it via Spring Data,
     * and returns the reconstituted domain model with updated version.</p>
     */
    @Override
    @Profiled(value = "containerRepository.save", slowThresholdMs = 500)
    public Container save(Container container) {
        log.debug("Persisting container: containerId={}, status={}",
                container.getContainerId(), container.getStatus());

        ContainerJpaEntity entity = mapper.toEntity(container);
        ContainerJpaEntity saved = jpaRepository.save(entity);

        log.debug("Container persisted: containerId={}, version={}",
                saved.getContainerId(), saved.getVersion());

        return mapper.toDomain(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the container by its ISO identifier and maps the result
     * back to the domain model if found.</p>
     */
    @Override
    public Optional<Container> findByContainerId(String containerId) {
        log.debug("Finding container: containerId={}", containerId);

        Optional<Container> result = jpaRepository.findByContainerId(containerId)
                .map(entity -> {
                    log.debug("Container found: containerId={}, status={}",
                            entity.getContainerId(), entity.getStatus());
                    return mapper.toDomain(entity);
                });

        if (result.isEmpty()) {
            log.warn("Container not found: containerId={}", containerId);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all containers associated with the given booking identifier,
     * ordered by creation date descending.</p>
     */
    @Override
    public List<Container> findByBookingId(UUID bookingId) {
        log.debug("Finding containers for booking: bookingId={}", bookingId);

        List<ContainerJpaEntity> entities = jpaRepository
                .findByBookingIdOrderByCreatedAtDesc(bookingId);

        log.debug("Found {} containers for booking: bookingId={}",
                entities.size(), bookingId);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the Spring Data repository's existence check by container ID.</p>
     */
    @Override
    public boolean existsByContainerId(String containerId) {
        boolean exists = jpaRepository.existsByContainerId(containerId);
        log.debug("Container exists check: containerId={}, exists={}", containerId, exists);
        return exists;
    }
}
