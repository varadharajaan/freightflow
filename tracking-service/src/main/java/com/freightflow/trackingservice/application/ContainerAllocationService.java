package com.freightflow.trackingservice.application;

import com.freightflow.trackingservice.domain.model.ContainerAllocation;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for managing container allocations.
 *
 * <p>This service handles the allocation and release of shipping containers for bookings.
 * When a booking is confirmed, containers are allocated from the available pool.
 * When cargo is delivered or a booking is cancelled, containers are released back.</p>
 *
 * <h3>Business Rules</h3>
 * <ul>
 *   <li>Each allocation generates a unique container ID in ISO 6346 format</li>
 *   <li>Multiple containers can be allocated to a single booking</li>
 *   <li>Releasing containers marks all allocations for a booking as RETURNED</li>
 * </ul>
 *
 * @see ContainerAllocation
 */
@Service
@Profiled(value = "ContainerAllocationService", slowThresholdMs = 1000)
public class ContainerAllocationService {

    private static final Logger log = LoggerFactory.getLogger(ContainerAllocationService.class);

    /** In-memory allocation store indexed by bookingId — production would use a repository port. */
    private final ConcurrentHashMap<UUID, List<ContainerAllocation>> allocationStore = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code ContainerAllocationService}.
     *
     * <p>No external dependencies required for the simplified in-memory implementation.
     * In a production system, this would accept a {@code ContainerAllocationRepository} port.</p>
     */
    public ContainerAllocationService() {
        // No external dependencies required for simplified implementation
    }

    // ==================== Commands ====================

    /**
     * Allocates the specified number of containers to a booking.
     *
     * <p>Each container is assigned a unique ISO 6346 container ID and starts in
     * {@link ContainerAllocation.ContainerAllocationStatus#ALLOCATED} status.</p>
     *
     * @param bookingId     the booking to allocate containers for
     * @param containerType the type of container (e.g. "DRY_20", "REEFER_40")
     * @param count         the number of containers to allocate
     * @return the list of allocated containers
     * @throws IllegalArgumentException if count is not positive
     */
    @Profiled(value = "allocateContainers", slowThresholdMs = 500)
    public List<ContainerAllocation> allocateContainers(UUID bookingId, String containerType, int count) {
        Objects.requireNonNull(bookingId, "Booking ID must not be null");
        Objects.requireNonNull(containerType, "Container type must not be null");

        if (count <= 0) {
            throw new IllegalArgumentException("Container count must be positive, got: " + count);
        }

        log.debug("Allocating containers: bookingId={}, type={}, count={}", bookingId, containerType, count);

        List<ContainerAllocation> allocations = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ContainerAllocation allocation = ContainerAllocation.allocate(bookingId, containerType);
            allocations.add(allocation);

            log.debug("Container allocated: containerId={}, allocationId={}",
                    allocation.getContainerId(), allocation.getAllocationId());
        }

        allocationStore.merge(bookingId, allocations, (existing, newOnes) -> {
            List<ContainerAllocation> merged = new ArrayList<>(existing);
            merged.addAll(newOnes);
            return merged;
        });

        log.info("Containers allocated: bookingId={}, type={}, count={}, containerIds={}",
                bookingId, containerType, count,
                allocations.stream().map(ContainerAllocation::getContainerId).toList());

        return Collections.unmodifiableList(allocations);
    }

    /**
     * Releases all containers allocated to a booking.
     *
     * <p>All containers for the specified booking are marked as
     * {@link ContainerAllocation.ContainerAllocationStatus#RETURNED}.</p>
     *
     * @param bookingId the booking whose containers should be released
     */
    @Profiled(value = "releaseContainers", slowThresholdMs = 500)
    public void releaseContainers(UUID bookingId) {
        Objects.requireNonNull(bookingId, "Booking ID must not be null");

        log.debug("Releasing containers: bookingId={}", bookingId);

        List<ContainerAllocation> allocations = allocationStore.get(bookingId);
        if (allocations == null || allocations.isEmpty()) {
            log.warn("No container allocations found for booking: bookingId={}", bookingId);
            return;
        }

        int releasedCount = 0;
        for (ContainerAllocation allocation : allocations) {
            if (allocation.getStatus() != ContainerAllocation.ContainerAllocationStatus.RETURNED) {
                allocation.release();
                releasedCount++;

                log.debug("Container released: containerId={}, allocationId={}",
                        allocation.getContainerId(), allocation.getAllocationId());
            }
        }

        log.info("Containers released: bookingId={}, released={}, total={}",
                bookingId, releasedCount, allocations.size());
    }

    // ==================== Queries ====================

    /**
     * Retrieves all container allocations for a booking.
     *
     * @param bookingId the booking identifier
     * @return the list of allocations (may be empty)
     */
    @Profiled(value = "getAllocations", slowThresholdMs = 200)
    public List<ContainerAllocation> getAllocations(UUID bookingId) {
        Objects.requireNonNull(bookingId, "Booking ID must not be null");

        log.debug("Fetching allocations: bookingId={}", bookingId);

        List<ContainerAllocation> allocations = allocationStore.getOrDefault(bookingId, List.of());

        log.info("Allocations retrieved: bookingId={}, count={}", bookingId, allocations.size());

        return Collections.unmodifiableList(allocations);
    }
}
