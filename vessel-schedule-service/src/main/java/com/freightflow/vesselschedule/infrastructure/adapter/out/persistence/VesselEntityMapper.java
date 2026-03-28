package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence;

import com.freightflow.vesselschedule.domain.model.Vessel;
import com.freightflow.vesselschedule.domain.model.Vessel.VesselStatus;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.VesselJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps between the JPA entity ({@link VesselJpaEntity}) and the domain model ({@link Vessel}).
 *
 * <p>This mapper is the translation layer between the persistence world (JPA annotations,
 * flat columns) and the domain world (aggregates, enums). It ensures the domain
 * model stays completely free of persistence concerns (Dependency Inversion Principle).</p>
 *
 * @see VesselJpaEntity
 * @see Vessel
 */
@Component
public class VesselEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(VesselEntityMapper.class);

    /**
     * Converts a domain {@link Vessel} aggregate to a {@link VesselJpaEntity} for persistence.
     *
     * @param vessel the domain vessel aggregate
     * @return the JPA entity ready for persistence
     */
    public VesselJpaEntity toEntity(Vessel vessel) {
        log.trace("Mapping domain Vessel to JPA entity: vesselId={}", vessel.getVesselId());

        var entity = new VesselJpaEntity();
        entity.setId(vessel.getVesselId());
        entity.setName(vessel.getName());
        entity.setImoNumber(vessel.getImoNumber());
        entity.setFlag(vessel.getFlag());
        entity.setCapacityTeu(vessel.getCapacityTeu());
        entity.setStatus(vessel.getStatus().name());
        entity.setVersion(vessel.getVersion());

        return entity;
    }

    /**
     * Reconstructs a domain {@link Vessel} aggregate from a {@link VesselJpaEntity}.
     *
     * <p>Uses {@link Vessel#reconstitute} to rebuild the aggregate from persisted state
     * without triggering any factory-method validation.</p>
     *
     * @param entity the JPA entity loaded from the database
     * @return the domain vessel aggregate in its persisted state
     */
    public Vessel toDomain(VesselJpaEntity entity) {
        log.trace("Mapping JPA entity to domain Vessel: vesselId={}", entity.getId());

        return Vessel.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getImoNumber(),
                entity.getFlag(),
                entity.getCapacityTeu(),
                VesselStatus.valueOf(entity.getStatus()),
                entity.getVersion()
        );
    }
}
