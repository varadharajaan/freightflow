package com.freightflow.trackingservice.application.query;

import com.freightflow.trackingservice.domain.model.Container;
import com.freightflow.trackingservice.domain.model.Milestone;
import com.freightflow.trackingservice.domain.port.ContainerRepository;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CQRS Query Handler — handles all <b>read</b> operations for the tracking domain.
 *
 * <p>This is the <b>read side</b> of CQRS. It queries container tracking data
 * including current positions, milestone history, and container status. The read
 * model is optimized for query performance with caching for frequently accessed data.</p>
 *
 * <h3>Separation from Write Side</h3>
 * <p>The write side ({@link com.freightflow.trackingservice.application.command.TrackingCommandHandler})
 * handles commands and produces events. This query handler reads the persisted state.
 * The two sides can be scaled independently.</p>
 *
 * @see com.freightflow.trackingservice.application.command.TrackingCommandHandler
 */
@Service
public class TrackingQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(TrackingQueryHandler.class);

    private final ContainerRepository containerRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param containerRepository the domain port for container persistence
     */
    public TrackingQueryHandler(ContainerRepository containerRepository) {
        this.containerRepository = Objects.requireNonNull(containerRepository,
                "containerRepository must not be null");
    }

    /**
     * Retrieves the current tracking state of a container.
     *
     * <h3>Spring Advanced Feature: @Cacheable</h3>
     * <p>Results are cached in the "containers" cache region. Cache is evicted
     * when the container state is updated by the command handler.</p>
     *
     * @param containerId the ISO container identifier
     * @return the container tracking state
     * @throws ResourceNotFoundException if no container exists for the given ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "containers", key = "#containerId", unless = "#result == null")
    @Profiled(value = "getContainerPosition", slowThresholdMs = 200)
    public Container getContainerPosition(String containerId) {
        log.debug("Querying container position: containerId={}", containerId);

        return containerRepository.findByContainerId(containerId)
                .orElseThrow(() -> {
                    log.warn("Container not found: containerId={}", containerId);
                    return new ResourceNotFoundException("Container", containerId);
                });
    }

    /**
     * Retrieves the milestone history for a container.
     *
     * <p>Returns the complete timeline of logistics milestones — gate-in, loaded,
     * departed, arrived, gate-out — in chronological order.</p>
     *
     * @param containerId the ISO container identifier
     * @return ordered list of milestones (may be empty if container has no milestones)
     * @throws ResourceNotFoundException if no container exists for the given ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "milestones", key = "#containerId", unless = "#result.isEmpty()")
    @Profiled(value = "getContainerMilestones", slowThresholdMs = 200)
    public List<Milestone> getMilestones(String containerId) {
        log.debug("Querying container milestones: containerId={}", containerId);

        Container container = containerRepository.findByContainerId(containerId)
                .orElseThrow(() -> {
                    log.warn("Container not found for milestones: containerId={}", containerId);
                    return new ResourceNotFoundException("Container", containerId);
                });

        List<Milestone> milestones = container.getMilestones();
        log.debug("Found {} milestone(s) for container: containerId={}", milestones.size(), containerId);

        return milestones;
    }

    /**
     * Retrieves all containers associated with a booking.
     *
     * @param bookingId the booking identifier
     * @return list of containers for the booking (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Profiled(value = "getContainersByBooking", slowThresholdMs = 300)
    public List<Container> getContainersByBooking(UUID bookingId) {
        log.debug("Querying containers by booking: bookingId={}", bookingId);

        List<Container> containers = containerRepository.findByBookingId(bookingId);
        log.debug("Found {} container(s) for booking: bookingId={}", containers.size(), bookingId);

        return containers;
    }
}
