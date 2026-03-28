package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence;

import com.freightflow.vesselschedule.domain.model.PortCall;
import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.domain.model.Voyage.VoyageStatus;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.PortCallJpaEntity;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.VoyageJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maps between the JPA entities ({@link VoyageJpaEntity}, {@link PortCallJpaEntity})
 * and the domain model ({@link Voyage}, {@link PortCall}).
 *
 * <p>This mapper is the translation layer between the persistence world (JPA annotations,
 * flat columns) and the domain world (aggregates, value objects). It ensures the domain
 * model stays completely free of persistence concerns (Dependency Inversion Principle).</p>
 *
 * <p><b>Round-trip guarantee:</b> All fields from the domain PortCall record — including
 * estimated and actual arrival/departure times — survive the domain → entity → domain
 * conversion cycle. No data is silently dropped.</p>
 *
 * @see VoyageJpaEntity
 * @see PortCallJpaEntity
 * @see Voyage
 * @see PortCall
 */
@Component
public class VoyageEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(VoyageEntityMapper.class);

    /**
     * Converts a domain {@link Voyage} aggregate to a {@link VoyageJpaEntity} for persistence.
     *
     * <p>Maps all scalar fields from the aggregate root, and converts each domain
     * {@link PortCall} to a {@link PortCallJpaEntity} with the correct sequence number
     * and parent reference. Existing port call entities are cleared and replaced to
     * reflect the current aggregate state (orphan removal handles deletions).</p>
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

        // Map port calls — preserve all fields from the domain PortCall record
        List<PortCallJpaEntity> portCallEntities = new ArrayList<>();
        List<PortCall> route = voyage.getRoute();
        for (int i = 0; i < route.size(); i++) {
            PortCall pc = route.get(i);
            PortCallJpaEntity pcEntity = toPortCallEntity(pc, entity, i + 1);
            portCallEntities.add(pcEntity);
        }

        entity.getPortCalls().clear();
        entity.getPortCalls().addAll(portCallEntities);

        log.trace("Mapped {} port call(s) for voyage: voyageId={}",
                portCallEntities.size(), voyage.getVoyageId());

        return entity;
    }

    /**
     * Reconstructs a domain {@link Voyage} aggregate from a {@link VoyageJpaEntity}.
     *
     * <p>Uses {@link Voyage#reconstitute} to rebuild the aggregate from persisted state
     * without triggering domain events. Port calls are fully reconstructed from the
     * {@link PortCallJpaEntity} children, preserving all estimated and actual timestamps.</p>
     *
     * @param entity the JPA entity loaded from the database
     * @return the domain voyage aggregate in its persisted state
     */
    public Voyage toDomain(VoyageJpaEntity entity) {
        log.trace("Mapping JPA entity to domain Voyage: voyageId={}", entity.getId());

        List<PortCall> portCalls = entity.getPortCalls().stream()
                .map(this::toPortCallDomain)
                .toList();

        log.trace("Mapped {} port call(s) from persistence for voyage: voyageId={}",
                portCalls.size(), entity.getId());

        return Voyage.reconstitute(
                entity.getId(),
                entity.getVesselId(),
                entity.getVoyageNumber(),
                VoyageStatus.valueOf(entity.getStatus()),
                entity.getTotalCapacityTeu(),
                entity.getRemainingCapacityTeu(),
                portCalls,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    /**
     * Converts a domain {@link PortCall} to a {@link PortCallJpaEntity}.
     *
     * <p>All five fields of the PortCall record (port, estimatedArrival, estimatedDeparture,
     * actualArrival, actualDeparture) are mapped. The sequence number and parent voyage
     * reference are set by the caller.</p>
     *
     * @param portCall       the domain port call value object
     * @param voyageEntity   the parent voyage JPA entity
     * @param sequenceNumber the 1-based position in the route
     * @return the JPA entity ready for cascade persistence
     */
    private PortCallJpaEntity toPortCallEntity(PortCall portCall,
                                                VoyageJpaEntity voyageEntity,
                                                int sequenceNumber) {
        var entity = new PortCallJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setVoyage(voyageEntity);
        entity.setPort(portCall.port());
        entity.setSequenceNumber(sequenceNumber);
        entity.setEstimatedArrival(portCall.estimatedArrival());
        entity.setEstimatedDeparture(portCall.estimatedDeparture());
        entity.setActualArrival(portCall.actualArrival());
        entity.setActualDeparture(portCall.actualDeparture());
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    /**
     * Converts a {@link PortCallJpaEntity} back to a domain {@link PortCall} record.
     *
     * <p>All five fields are preserved — no data is silently dropped during the
     * entity-to-domain conversion.</p>
     *
     * @param entity the JPA port call entity
     * @return the domain port call value object
     */
    private PortCall toPortCallDomain(PortCallJpaEntity entity) {
        return new PortCall(
                entity.getPort(),
                entity.getEstimatedArrival(),
                entity.getEstimatedDeparture(),
                entity.getActualArrival(),
                entity.getActualDeparture()
        );
    }
}
