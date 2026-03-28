package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence;

import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.vesselschedule.domain.model.Vessel;
import com.freightflow.vesselschedule.domain.port.VesselRepository;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.VesselJpaEntity;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.repository.SpringDataVesselRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter implementing the domain's {@link VesselRepository} port.
 *
 * <p>This is the <b>outbound adapter</b> in Hexagonal Architecture. It translates
 * between the domain model ({@link Vessel}) and the JPA entity ({@link VesselJpaEntity})
 * using the {@link VesselEntityMapper}.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts Spring Data JPA to the domain port interface</li>
 *   <li><b>Dependency Inversion</b> — domain defines the port, infrastructure implements it</li>
 *   <li><b>Mapper isolation</b> — JPA entities never leak into the domain layer</li>
 *   <li><b>Logging</b> — DEBUG for operations, WARN for edge cases</li>
 * </ul>
 *
 * @see VesselRepository
 * @see SpringDataVesselRepository
 * @see VesselEntityMapper
 */
@Component
public class JpaVesselPersistenceAdapter implements VesselRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaVesselPersistenceAdapter.class);

    private final SpringDataVesselRepository jpaRepository;
    private final VesselEntityMapper mapper;

    /**
     * Constructor injection — depends on Spring Data repository and entity mapper.
     *
     * @param jpaRepository the Spring Data JPA repository for vessel entities
     * @param mapper        the entity mapper for domain-to-JPA translation
     */
    public JpaVesselPersistenceAdapter(SpringDataVesselRepository jpaRepository,
                                        VesselEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "JPA repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the domain vessel to a JPA entity, persists it via Spring Data,
     * and returns the reconstituted domain model with updated version.</p>
     */
    @Override
    @Profiled(value = "vesselRepository.save", slowThresholdMs = 500)
    public Vessel save(Vessel vessel) {
        log.debug("Persisting vessel: vesselId={}, name={}, status={}",
                vessel.getVesselId(), vessel.getName(), vessel.getStatus());

        VesselJpaEntity entity = mapper.toEntity(vessel);
        VesselJpaEntity saved = jpaRepository.save(entity);

        log.debug("Vessel persisted: vesselId={}, version={}",
                saved.getId(), saved.getVersion());

        return mapper.toDomain(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the vessel by its UUID and maps the result back to the domain
     * model if found.</p>
     */
    @Override
    public Optional<Vessel> findById(UUID vesselId) {
        log.debug("Finding vessel: vesselId={}", vesselId);

        Optional<Vessel> result = jpaRepository.findById(vesselId)
                .map(entity -> {
                    log.debug("Vessel found: vesselId={}, name={}, status={}",
                            entity.getId(), entity.getName(), entity.getStatus());
                    return mapper.toDomain(entity);
                });

        if (result.isEmpty()) {
            log.warn("Vessel not found: vesselId={}", vesselId);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the vessel by its IMO identification number and maps the result
     * back to the domain model if found.</p>
     */
    @Override
    public Optional<Vessel> findByImoNumber(String imoNumber) {
        log.debug("Finding vessel by IMO number: imoNumber={}", imoNumber);

        Optional<Vessel> result = jpaRepository.findByImoNumber(imoNumber)
                .map(entity -> {
                    log.debug("Vessel found by IMO: vesselId={}, imoNumber={}",
                            entity.getId(), entity.getImoNumber());
                    return mapper.toDomain(entity);
                });

        if (result.isEmpty()) {
            log.warn("Vessel not found by IMO number: imoNumber={}", imoNumber);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all vessels with ACTIVE status, ordered by capacity descending.</p>
     */
    @Override
    public List<Vessel> findAllActive() {
        log.debug("Finding all active vessels");

        List<VesselJpaEntity> entities = jpaRepository.findAllActiveVessels();

        log.debug("Found {} active vessels", entities.size());

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }
}
