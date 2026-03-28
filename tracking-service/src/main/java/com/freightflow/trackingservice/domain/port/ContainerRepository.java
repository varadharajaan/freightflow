package com.freightflow.trackingservice.domain.port;

import com.freightflow.trackingservice.domain.model.Container;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for container persistence.
 *
 * <p>This interface defines the contract that the domain layer expects from
 * the persistence layer (Dependency Inversion Principle). The domain does NOT
 * depend on JPA, Hibernate, or any infrastructure technology.</p>
 *
 * <p>The implementation (JPA adapter) lives in the infrastructure layer
 * and adapts this port to Spring Data JPA.</p>
 *
 * @see com.freightflow.trackingservice.infrastructure.adapter.out.persistence
 */
public interface ContainerRepository {

    /**
     * Persists a new or updated container.
     *
     * @param container the container aggregate to save
     * @return the saved container (with updated version)
     */
    Container save(Container container);

    /**
     * Finds a container by its ISO identifier.
     *
     * @param containerId the container identifier (ISO format)
     * @return the container, or empty if not found
     */
    Optional<Container> findByContainerId(String containerId);

    /**
     * Finds all containers associated with a booking.
     *
     * @param bookingId the booking identifier
     * @return list of containers (may be empty)
     */
    List<Container> findByBookingId(UUID bookingId);

    /**
     * Checks whether a container exists.
     *
     * @param containerId the container identifier (ISO format)
     * @return true if the container exists
     */
    boolean existsByContainerId(String containerId);
}
