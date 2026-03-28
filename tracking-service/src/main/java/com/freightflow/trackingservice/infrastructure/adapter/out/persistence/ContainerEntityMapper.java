package com.freightflow.trackingservice.infrastructure.adapter.out.persistence;

import com.freightflow.trackingservice.domain.model.Container;
import com.freightflow.trackingservice.domain.model.ContainerStatus;
import com.freightflow.trackingservice.domain.model.Position;
import com.freightflow.trackingservice.domain.model.Position.PositionSource;
import com.freightflow.trackingservice.infrastructure.adapter.out.persistence.entity.ContainerJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps between the JPA entity ({@link ContainerJpaEntity}) and the domain model ({@link Container}).
 *
 * <p>This mapper is the translation layer between the persistence world (JPA annotations,
 * flat columns) and the domain world (aggregates, value objects). It ensures the domain
 * model stays completely free of persistence concerns (Dependency Inversion Principle).</p>
 *
 * <p>A hand-written mapper is used because the Container aggregate has a private constructor,
 * domain events, and value objects that MapStruct cannot handle out of the box.</p>
 *
 * @see ContainerJpaEntity
 * @see Container
 */
@Component
public class ContainerEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(ContainerEntityMapper.class);

    /**
     * Converts a domain {@link Container} aggregate to a {@link ContainerJpaEntity} for persistence.
     *
     * @param container the domain container aggregate
     * @return the JPA entity ready for persistence
     */
    public ContainerJpaEntity toEntity(Container container) {
        log.trace("Mapping domain Container to JPA entity: containerId={}", container.getContainerId());

        var entity = new ContainerJpaEntity();
        entity.setContainerId(container.getContainerId());
        entity.setBookingId(container.getBookingId());
        entity.setStatus(container.getStatus().name());

        // Position fields (flattened from value object)
        container.getCurrentPosition().ifPresent(position -> {
            entity.setLatitude(position.latitude());
            entity.setLongitude(position.longitude());
            entity.setPositionTimestamp(position.timestamp());
            entity.setPositionSource(position.source().name());
        });

        // Optional voyage reference
        container.getVoyageId().ifPresent(entity::setVoyageId);

        // Audit fields
        entity.setCreatedAt(container.getCreatedAt());
        entity.setUpdatedAt(container.getUpdatedAt());
        entity.setVersion(container.getVersion());

        return entity;
    }

    /**
     * Reconstructs a domain {@link Container} aggregate from a {@link ContainerJpaEntity}.
     *
     * <p>Uses {@link Container#reconstitute} to rebuild the aggregate from persisted state
     * without triggering domain events. Position is reconstructed as a value object if
     * all required fields are present in the entity.</p>
     *
     * @param entity the JPA entity loaded from the database
     * @return the domain container aggregate in its persisted state
     */
    public Container toDomain(ContainerJpaEntity entity) {
        log.trace("Mapping JPA entity to domain Container: containerId={}", entity.getContainerId());

        // Reconstruct position value object if coordinates are present
        Position position = null;
        if (entity.getLatitude() != null && entity.getLongitude() != null
                && entity.getPositionTimestamp() != null && entity.getPositionSource() != null) {
            position = new Position(
                    entity.getLatitude(),
                    entity.getLongitude(),
                    entity.getPositionTimestamp(),
                    PositionSource.valueOf(entity.getPositionSource())
            );
        }

        return Container.reconstitute(
                entity.getContainerId(),
                entity.getBookingId(),
                ContainerStatus.valueOf(entity.getStatus()),
                position,
                entity.getVoyageId(),
                List.of(), // Milestones are not persisted in the containers table
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
