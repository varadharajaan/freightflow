package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence;

import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.domain.model.Voyage.VoyageStatus;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.VoyageJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps between the JPA entity ({@link VoyageJpaEntity}) and the domain model ({@link Voyage}).
 *
 * <p>This mapper is the translation layer between the persistence world (JPA annotations,
 * flat columns) and the domain world (aggregates, value objects). It ensures the domain
 * model stays completely free of persistence concerns (Dependency Inversion Principle).</p>
 *
 * @see VoyageJpaEntity
 * @see Voyage
 */
@Component
public class VoyageEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(VoyageEntityMapper.class);

    /**
     * Converts a domain {@link Voyage} aggregate to a {@link VoyageJpaEntity} for persistence.
     *
     * @param voyage the domain voyage aggregate
     * @return the JPA entity ready for persistence
     */
    public VoyageJpaEntity toEntity(Voyage voyage) {
        log.trace("Mapping domain Voyage to JPA entity: voyageId={}", voyage.getVoyageId());

        var entity = new VoyageJpaEntity();
        entity.setId(voyage.getVoyageId());
        entity.setVesselId(voyage.getVesselId());
        entity.setVoyageNumber(voyage.getVoyageNumber());
        entity.setStatus(voyage.getStatus().name());
        entity.setTotalCapacityTeu(voyage.getTotalCapacityTeu());
        entity.setRemainingCapacityTeu(voyage.getRemainingCapacityTeu());
        entity.setCreatedAt(voyage.getCreatedAt());
        entity.setUpdatedAt(voyage.getUpdatedAt());
        entity.setVersion(voyage.getVersion());

        return entity;
    }

    /**
     * Reconstructs a domain {@link Voyage} aggregate from a {@link VoyageJpaEntity}.
     *
     * <p>Uses {@link Voyage#reconstitute} to rebuild the aggregate from persisted state
     * without triggering domain events. The route (port calls) is not stored in the
     * voyages table, so an empty list is provided during reconstitution.</p>
     *
     * @param entity the JPA entity loaded from the database
     * @return the domain voyage aggregate in its persisted state
     */
    public Voyage toDomain(VoyageJpaEntity entity) {
        log.trace("Mapping JPA entity to domain Voyage: voyageId={}", entity.getId());

        return Voyage.reconstitute(
                entity.getId(),
                entity.getVesselId(),
                entity.getVoyageNumber(),
                VoyageStatus.valueOf(entity.getStatus()),
                entity.getTotalCapacityTeu(),
                entity.getRemainingCapacityTeu(),
                List.of(), // Port calls are not stored in the voyages table
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
