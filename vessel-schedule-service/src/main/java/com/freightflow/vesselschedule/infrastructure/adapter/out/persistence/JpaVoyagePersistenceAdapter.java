package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence;

import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.domain.port.VoyageRepository;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.VoyageJpaEntity;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.repository.SpringDataVoyageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter implementing the domain's {@link VoyageRepository} port.
 *
 * <p>This is the <b>outbound adapter</b> in Hexagonal Architecture. It translates
 * between the domain model ({@link Voyage}) and the JPA entity ({@link VoyageJpaEntity})
 * using the {@link VoyageEntityMapper}.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts Spring Data JPA to the domain port interface</li>
 *   <li><b>Dependency Inversion</b> — domain defines the port, infrastructure implements it</li>
 *   <li><b>Mapper isolation</b> — JPA entities never leak into the domain layer</li>
 *   <li><b>Logging</b> — DEBUG for operations, WARN for edge cases</li>
 * </ul>
 *
 * @see VoyageRepository
 * @see SpringDataVoyageRepository
 * @see VoyageEntityMapper
 */
@Component
public class JpaVoyagePersistenceAdapter implements VoyageRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaVoyagePersistenceAdapter.class);

    private final SpringDataVoyageRepository jpaRepository;
    private final VoyageEntityMapper mapper;

    /**
     * Constructor injection — depends on Spring Data repository and entity mapper.
     *
     * @param jpaRepository the Spring Data JPA repository for voyage entities
     * @param mapper        the entity mapper for domain-to-JPA translation
     */
    public JpaVoyagePersistenceAdapter(SpringDataVoyageRepository jpaRepository,
                                        VoyageEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "JPA repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the domain voyage to a JPA entity, persists it via Spring Data,
     * and returns the reconstituted domain model with updated version.</p>
     */
    @Override
    @Profiled(value = "voyageRepository.save", slowThresholdMs = 500)
    public Voyage save(Voyage voyage) {
        log.debug("Persisting voyage: voyageId={}, voyageNumber={}, status={}",
                voyage.getVoyageId(), voyage.getVoyageNumber(), voyage.getStatus());

        VoyageJpaEntity entity = mapper.toEntity(voyage);
        VoyageJpaEntity saved = jpaRepository.save(entity);

        log.debug("Voyage persisted: voyageId={}, version={}",
                saved.getId(), saved.getVersion());

        return mapper.toDomain(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the voyage by its UUID and maps the result back to the domain
     * model if found.</p>
     */
    @Override
    public Optional<Voyage> findById(UUID voyageId) {
        log.debug("Finding voyage: voyageId={}", voyageId);

        Optional<Voyage> result = jpaRepository.findById(voyageId)
                .map(entity -> {
                    log.debug("Voyage found: voyageId={}, voyageNumber={}, status={}",
                            entity.getId(), entity.getVoyageNumber(), entity.getStatus());
                    return mapper.toDomain(entity);
                });

        if (result.isEmpty()) {
            log.warn("Voyage not found: voyageId={}", voyageId);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all voyages associated with the given vessel identifier,
     * ordered by creation date descending.</p>
     */
    @Override
    public List<Voyage> findByVesselId(UUID vesselId) {
        log.debug("Finding voyages for vessel: vesselId={}", vesselId);

        List<VoyageJpaEntity> entities = jpaRepository
                .findByVesselIdOrderByCreatedAtDesc(vesselId);

        log.debug("Found {} voyages for vessel: vesselId={}",
                entities.size(), vesselId);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Finds voyages in SCHEDULED status with at least the specified remaining
     * TEU capacity, ordered by remaining capacity descending.</p>
     */
    @Override
    public List<Voyage> findScheduledWithCapacity(int minCapacityTeu) {
        log.debug("Finding scheduled voyages with minimum capacity: minCapacityTeu={}", minCapacityTeu);

        List<VoyageJpaEntity> entities = jpaRepository
                .findScheduledWithCapacity(minCapacityTeu);

        log.debug("Found {} scheduled voyages with minimum capacity {}TEU",
                entities.size(), minCapacityTeu);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the Spring Data repository's existence check by voyage ID.</p>
     */
    @Override
    public boolean existsById(UUID voyageId) {
        boolean exists = jpaRepository.existsById(voyageId);
        log.debug("Voyage exists check: voyageId={}, exists={}", voyageId, exists);
        return exists;
    }
}
