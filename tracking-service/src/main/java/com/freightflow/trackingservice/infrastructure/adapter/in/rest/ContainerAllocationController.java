package com.freightflow.trackingservice.infrastructure.adapter.in.rest;

import com.freightflow.trackingservice.application.ContainerAllocationService;
import com.freightflow.trackingservice.domain.model.ContainerAllocation;
import com.freightflow.commons.observability.profiling.Profiled;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for managing container allocations.
 *
 * <p>This is an inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer calls for container allocation and release.
 * All business logic is delegated to {@link ContainerAllocationService}.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST   /api/v1/tracking/allocations}              — allocate containers for a booking</li>
 *   <li>{@code DELETE  /api/v1/tracking/allocations/{bookingId}}  — release all containers for a booking</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <p>Container allocation is an operational action restricted to ADMIN and OPERATOR roles.</p>
 *
 * @see ContainerAllocationService
 */
@RestController
@RequestMapping("/api/v1/tracking/allocations")
public class ContainerAllocationController {

    private static final Logger log = LoggerFactory.getLogger(ContainerAllocationController.class);

    private final ContainerAllocationService allocationService;

    /**
     * Creates a new {@code ContainerAllocationController} with the required application service.
     *
     * @param allocationService the container allocation service (must not be null)
     */
    public ContainerAllocationController(ContainerAllocationService allocationService) {
        this.allocationService = Objects.requireNonNull(allocationService,
                "ContainerAllocationService must not be null");
    }

    /**
     * Allocates containers for a booking.
     *
     * <p>Creates the specified number of container allocations, each with a unique
     * ISO 6346 container ID. The containers start in ALLOCATED status.</p>
     *
     * @param request the allocation request containing booking ID, container type, and count
     * @return 201 Created with the list of allocated containers
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping
    @Profiled(value = "allocateContainersEndpoint", slowThresholdMs = 500)
    public ResponseEntity<List<AllocationResponse>> allocateContainers(
            @Valid @RequestBody AllocationRequest request) {

        log.debug("POST /api/v1/tracking/allocations — allocating: bookingId={}, type={}, count={}",
                request.bookingId(), request.containerType(), request.count());

        UUID bookingId = UUID.fromString(request.bookingId());

        List<ContainerAllocation> allocations = allocationService.allocateContainers(
                bookingId, request.containerType(), request.count());

        List<AllocationResponse> responses = allocations.stream()
                .map(AllocationResponse::from)
                .toList();

        log.info("Containers allocated: bookingId={}, count={}", request.bookingId(), responses.size());

        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    /**
     * Releases all containers allocated to a booking.
     *
     * <p>Marks all container allocations for the specified booking as RETURNED.</p>
     *
     * @param bookingId the booking UUID (path variable)
     * @return 204 No Content
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @DeleteMapping("/{bookingId}")
    @Profiled(value = "releaseContainersEndpoint", slowThresholdMs = 500)
    public ResponseEntity<Void> releaseContainers(@PathVariable UUID bookingId) {
        log.debug("DELETE /api/v1/tracking/allocations/{} — releasing containers", bookingId);

        allocationService.releaseContainers(bookingId);

        log.info("Containers released: bookingId={}", bookingId);

        return ResponseEntity.noContent().build();
    }

    // ==================== Request / Response DTOs ====================

    /**
     * Inbound DTO for container allocation requests.
     *
     * @param bookingId     the booking UUID to allocate containers for
     * @param containerType the type of container (e.g. "DRY_20", "REEFER_40")
     * @param count         the number of containers to allocate
     */
    public record AllocationRequest(

            @NotBlank(message = "Booking ID is required")
            String bookingId,

            @NotBlank(message = "Container type is required")
            String containerType,

            @NotNull(message = "Container count is required")
            @Positive(message = "Container count must be positive")
            Integer count
    ) {
    }

    /**
     * Outbound DTO for container allocation responses.
     *
     * @param allocationId  the unique allocation identifier
     * @param containerId   the ISO 6346 container identifier
     * @param bookingId     the associated booking UUID
     * @param containerType the type of container
     * @param status        the current allocation status
     * @param allocatedAt   when the container was allocated
     * @param returnedAt    when the container was returned (null if not yet returned)
     */
    public record AllocationResponse(
            UUID allocationId,
            String containerId,
            UUID bookingId,
            String containerType,
            String status,
            Instant allocatedAt,
            Instant returnedAt
    ) {

        /**
         * Factory method that maps a domain {@link ContainerAllocation} to an API response DTO.
         *
         * @param allocation the domain container allocation entity
         * @return a new {@code AllocationResponse} DTO
         */
        public static AllocationResponse from(ContainerAllocation allocation) {
            return new AllocationResponse(
                    allocation.getAllocationId(),
                    allocation.getContainerId(),
                    allocation.getBookingId(),
                    allocation.getContainerType(),
                    allocation.getStatus().name(),
                    allocation.getAllocatedAt(),
                    allocation.getReturnedAt()
            );
        }
    }
}
