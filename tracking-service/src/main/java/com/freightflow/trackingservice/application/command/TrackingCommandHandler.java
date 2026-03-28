package com.freightflow.trackingservice.application.command;

import com.freightflow.trackingservice.domain.event.TrackingEvent;
import com.freightflow.trackingservice.domain.model.Container;
import com.freightflow.trackingservice.domain.model.Position;
import com.freightflow.trackingservice.domain.port.ContainerRepository;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CQRS Command Handler — handles all write operations for the container tracking aggregate.
 *
 * <p>This is the <b>write side</b> of CQRS. It:</p>
 * <ol>
 *   <li>Receives a tracking command</li>
 *   <li>Loads the container aggregate (or creates a new one)</li>
 *   <li>Executes domain logic on the aggregate</li>
 *   <li>Persists the aggregate state</li>
 *   <li>Publishes domain events for downstream consumers</li>
 * </ol>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Command Handler</b> — each method handles a specific tracking intention</li>
 *   <li><b>CQRS</b> — write operations separated from read operations</li>
 * </ul>
 *
 * @see com.freightflow.trackingservice.application.query.TrackingQueryHandler
 */
@Service
public class TrackingCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TrackingCommandHandler.class);

    private final ContainerRepository containerRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param containerRepository the domain port for container persistence
     */
    public TrackingCommandHandler(ContainerRepository containerRepository) {
        this.containerRepository = Objects.requireNonNull(containerRepository,
                "containerRepository must not be null");
    }

    /**
     * Creates a new container tracking record when a booking is created.
     *
     * <p>Called by the Kafka consumer when a {@code BookingCreated} event is received.
     * The container starts in EMPTY status.</p>
     *
     * @param containerId the ISO container identifier
     * @param bookingId   the booking this container is associated with
     * @return the created container
     */
    @Transactional
    @Profiled(value = "createContainerTracking", slowThresholdMs = 500)
    public Container createContainer(String containerId, UUID bookingId) {
        log.debug("Creating container tracking record: containerId={}, bookingId={}",
                containerId, bookingId);

        Container container = Container.create(containerId, bookingId);
        Container saved = containerRepository.save(container);

        log.info("Container tracking record created: containerId={}, bookingId={}, status={}",
                saved.getContainerId(), bookingId, saved.getStatus());

        return saved;
    }

    /**
     * Updates the position of a tracked container.
     *
     * @param containerId the ISO container identifier
     * @param position    the new position reading
     * @return the updated container
     * @throws ResourceNotFoundException if the container is not found
     */
    @Transactional
    @Profiled(value = "updateContainerPosition", slowThresholdMs = 300)
    public Container updatePosition(String containerId, Position position) {
        log.debug("Updating container position: containerId={}, lat={}, lon={}",
                containerId, position.latitude(), position.longitude());

        Container container = loadContainerOrThrow(containerId);
        container.updatePosition(position);

        Container saved = containerRepository.save(container);
        List<TrackingEvent> events = saved.pullDomainEvents();

        log.info("Container position updated: containerId={}, source={}",
                containerId, position.source());

        return saved;
    }

    /**
     * Records cargo loading into a container.
     *
     * @param containerId the ISO container identifier
     * @return the updated container
     * @throws ResourceNotFoundException if the container is not found
     */
    @Transactional
    @Profiled(value = "loadContainerCargo", slowThresholdMs = 500)
    public Container loadCargo(String containerId) {
        log.debug("Loading cargo into container: containerId={}", containerId);

        Container container = loadContainerOrThrow(containerId);
        container.loadCargo();

        Container saved = containerRepository.save(container);
        List<TrackingEvent> events = saved.pullDomainEvents();

        log.info("Cargo loaded: containerId={}, status={}", containerId, saved.getStatus());

        return saved;
    }

    /**
     * Records container departure on a voyage.
     *
     * @param containerId the ISO container identifier
     * @param voyageId    the voyage the container is departing on
     * @return the updated container
     * @throws ResourceNotFoundException if the container is not found
     */
    @Transactional
    @Profiled(value = "departContainer", slowThresholdMs = 500)
    public Container depart(String containerId, UUID voyageId) {
        log.debug("Container departing: containerId={}, voyageId={}", containerId, voyageId);

        Container container = loadContainerOrThrow(containerId);
        container.depart(voyageId);

        Container saved = containerRepository.save(container);
        List<TrackingEvent> events = saved.pullDomainEvents();

        log.info("Container departed: containerId={}, voyageId={}, status={}",
                containerId, voyageId, saved.getStatus());

        return saved;
    }

    /**
     * Records container arrival / cargo unloading at a port.
     *
     * @param containerId the ISO container identifier
     * @param port        the port where the container arrived
     * @return the updated container
     * @throws ResourceNotFoundException if the container is not found
     */
    @Transactional
    @Profiled(value = "unloadContainerCargo", slowThresholdMs = 500)
    public Container unloadCargo(String containerId, String port) {
        log.debug("Unloading cargo from container: containerId={}, port={}", containerId, port);

        Container container = loadContainerOrThrow(containerId);
        container.unloadCargo(port);

        Container saved = containerRepository.save(container);
        List<TrackingEvent> events = saved.pullDomainEvents();

        log.info("Cargo unloaded: containerId={}, port={}, status={}",
                containerId, port, saved.getStatus());

        return saved;
    }

    /**
     * Marks a container as delivered to the consignee.
     *
     * @param containerId the ISO container identifier
     * @return the updated container
     * @throws ResourceNotFoundException if the container is not found
     */
    @Transactional
    @Profiled(value = "markContainerDelivered", slowThresholdMs = 500)
    public Container markDelivered(String containerId) {
        log.debug("Marking container as delivered: containerId={}", containerId);

        Container container = loadContainerOrThrow(containerId);
        container.markDelivered();

        Container saved = containerRepository.save(container);
        List<TrackingEvent> events = saved.pullDomainEvents();

        log.info("Container delivered: containerId={}, status={}", containerId, saved.getStatus());

        return saved;
    }

    private Container loadContainerOrThrow(String containerId) {
        return containerRepository.findByContainerId(containerId)
                .orElseThrow(() -> {
                    log.warn("Container not found: containerId={}", containerId);
                    return new ResourceNotFoundException("Container", containerId);
                });
    }
}
