package com.freightflow.vesselschedule.infrastructure.adapter.in.rest;

import com.freightflow.vesselschedule.application.command.VesselCommandHandler;
import com.freightflow.vesselschedule.application.query.VesselQueryHandler;
import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.infrastructure.adapter.in.rest.dto.CapacityRequest;
import com.freightflow.vesselschedule.infrastructure.adapter.in.rest.dto.CapacityResponse;
import com.freightflow.commons.observability.profiling.Profiled;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for vessel capacity management operations.
 *
 * <p>This controller provides the HTTP API that the booking service's
 * {@code VesselCapacityPort} adapter calls to reserve and release TEU capacity
 * on scheduled voyages. It bridges the inter-service communication gap by
 * exposing capacity operations as REST endpoints.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/voyages/{voyageId}/reserve-capacity}  — reserve TEU on a voyage</li>
 *   <li>{@code POST /api/v1/voyages/{voyageId}/release-capacity}  — release TEU back to a voyage</li>
 *   <li>{@code GET  /api/v1/voyages/{voyageId}/capacity}          — query remaining capacity</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * <p>Reserve and release operations accept an {@code idempotencyKey} in the request body.
 * This key is logged for traceability but deduplication logic is handled by the
 * underlying command handler and domain model.</p>
 *
 * @see VesselCommandHandler
 * @see VesselQueryHandler
 * @see com.freightflow.booking.application.port.VesselCapacityPort
 */
@RestController
@RequestMapping("/api/v1/voyages")
@Profiled(value = "CapacityController", slowThresholdMs = 1000)
public class CapacityController {

    private static final Logger log = LoggerFactory.getLogger(CapacityController.class);

    private final VesselCommandHandler commandHandler;
    private final VesselQueryHandler queryHandler;

    /**
     * Creates a new {@code CapacityController} with the required handlers.
     *
     * @param commandHandler the vessel command handler for write operations (must not be null)
     * @param queryHandler   the vessel query handler for read operations (must not be null)
     */
    public CapacityController(VesselCommandHandler commandHandler,
                               VesselQueryHandler queryHandler) {
        this.commandHandler = Objects.requireNonNull(commandHandler,
                "VesselCommandHandler must not be null");
        this.queryHandler = Objects.requireNonNull(queryHandler,
                "VesselQueryHandler must not be null");
    }

    /**
     * Reserves TEU capacity on a voyage for a booking.
     *
     * <p>The requested TEU is deducted from the voyage's remaining capacity.
     * The operation fails if insufficient capacity remains or the voyage has departed.</p>
     *
     * @param voyageId the voyage UUID (path variable)
     * @param request  the capacity reservation request (validated via Bean Validation)
     * @return 200 OK with the updated capacity state and reservation confirmation
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping("/{voyageId}/reserve-capacity")
    @Profiled(value = "reserveCapacityEndpoint", slowThresholdMs = 500)
    public ResponseEntity<CapacityResponse> reserveCapacity(
            @PathVariable UUID voyageId,
            @Valid @RequestBody CapacityRequest request) {

        log.debug("POST /api/v1/voyages/{}/reserve-capacity — bookingId={}, teu={}, idempotencyKey={}",
                voyageId, request.bookingId(), request.teu(), request.idempotencyKey());

        UUID bookingId = UUID.fromString(request.bookingId());
        int teuRequired = (int) Math.ceil(request.teu());

        Voyage voyage = commandHandler.reserveCapacity(voyageId, bookingId, teuRequired);

        CapacityResponse response = new CapacityResponse(
                voyage.getVoyageId(),
                voyage.getTotalCapacityTeu(),
                voyage.getRemainingCapacityTeu(),
                true
        );

        log.info("Capacity reserved: voyageId={}, bookingId={}, teu={}, remaining={}",
                voyageId, request.bookingId(), teuRequired, response.remainingCapacityTeu());

        return ResponseEntity.ok(response);
    }

    /**
     * Releases previously reserved TEU capacity back to a voyage.
     *
     * <p>The released TEU is added back to the voyage's remaining capacity,
     * up to the total capacity limit.</p>
     *
     * @param voyageId the voyage UUID (path variable)
     * @param request  the capacity release request (validated via Bean Validation)
     * @return 200 OK with the updated capacity state
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping("/{voyageId}/release-capacity")
    @Profiled(value = "releaseCapacityEndpoint", slowThresholdMs = 500)
    public ResponseEntity<CapacityResponse> releaseCapacity(
            @PathVariable UUID voyageId,
            @Valid @RequestBody CapacityRequest request) {

        log.debug("POST /api/v1/voyages/{}/release-capacity — bookingId={}, teu={}, idempotencyKey={}",
                voyageId, request.bookingId(), request.teu(), request.idempotencyKey());

        int teuToRelease = (int) Math.ceil(request.teu());

        Voyage voyage = commandHandler.releaseCapacity(voyageId, teuToRelease);

        CapacityResponse response = new CapacityResponse(
                voyage.getVoyageId(),
                voyage.getTotalCapacityTeu(),
                voyage.getRemainingCapacityTeu(),
                true
        );

        log.info("Capacity released: voyageId={}, bookingId={}, teu={}, remaining={}",
                voyageId, request.bookingId(), teuToRelease, response.remainingCapacityTeu());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the current capacity state of a voyage.
     *
     * @param voyageId the voyage UUID (path variable)
     * @return 200 OK with the capacity information
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{voyageId}/capacity")
    @Profiled(value = "getCapacityEndpoint", slowThresholdMs = 200)
    public ResponseEntity<CapacityResponse> getCapacity(@PathVariable UUID voyageId) {
        log.debug("GET /api/v1/voyages/{}/capacity — fetching capacity", voyageId);

        Voyage voyage = queryHandler.getVoyage(voyageId);

        CapacityResponse response = new CapacityResponse(
                voyage.getVoyageId(),
                voyage.getTotalCapacityTeu(),
                voyage.getRemainingCapacityTeu(),
                false
        );

        log.info("Capacity retrieved: voyageId={}, remaining={}/{}",
                voyageId, response.remainingCapacityTeu(), response.totalCapacityTeu());

        return ResponseEntity.ok(response);
    }
}
