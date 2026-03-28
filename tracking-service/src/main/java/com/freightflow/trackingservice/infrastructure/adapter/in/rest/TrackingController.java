package com.freightflow.trackingservice.infrastructure.adapter.in.rest;

import com.freightflow.trackingservice.application.command.TrackingCommandHandler;
import com.freightflow.trackingservice.application.query.TrackingQueryHandler;
import com.freightflow.trackingservice.domain.model.Container;
import com.freightflow.trackingservice.domain.model.Milestone;
import com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto.ContainerResponse;
import com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto.MilestoneResponse;
import com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto.UpdatePositionRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for container tracking operations.
 *
 * <p>This is the primary inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer commands and queries. It delegates all business
 * logic to the command and query handlers and maps domain objects to REST DTOs.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET  /api/v1/tracking/containers/{id}}             — get container tracking state</li>
 *   <li>{@code GET  /api/v1/tracking/containers/{id}/milestones}  — get container milestones</li>
 *   <li>{@code POST /api/v1/tracking/containers/{id}/position}    — update container position</li>
 *   <li>{@code GET  /api/v1/tracking/bookings/{bookingId}/containers} — get containers for booking</li>
 * </ul>
 *
 * @see TrackingCommandHandler
 * @see TrackingQueryHandler
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);

    private final TrackingQueryHandler queryHandler;
    private final TrackingCommandHandler commandHandler;

    /**
     * Creates a new {@code TrackingController} with the required handlers.
     *
     * @param queryHandler   the tracking query handler (must not be null)
     * @param commandHandler the tracking command handler (must not be null)
     */
    public TrackingController(TrackingQueryHandler queryHandler,
                              TrackingCommandHandler commandHandler) {
        this.queryHandler = Objects.requireNonNull(queryHandler, "TrackingQueryHandler must not be null");
        this.commandHandler = Objects.requireNonNull(commandHandler, "TrackingCommandHandler must not be null");
    }

    /**
     * Retrieves the current tracking state of a container.
     *
     * @param containerId the ISO container identifier (path variable)
     * @return 200 OK with the container tracking details
     */
    @GetMapping("/containers/{containerId}")
    public ResponseEntity<ContainerResponse> getContainer(@PathVariable String containerId) {
        log.debug("GET /api/v1/tracking/containers/{} — fetching container", containerId);

        Container container = queryHandler.getContainerPosition(containerId);
        ContainerResponse response = ContainerResponse.from(container);

        log.info("Container retrieved: containerId={}, status={}", response.containerId(), response.status());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the milestone history for a container.
     *
     * @param containerId the ISO container identifier (path variable)
     * @return 200 OK with a list of milestones (may be empty)
     */
    @GetMapping("/containers/{containerId}/milestones")
    public ResponseEntity<List<MilestoneResponse>> getMilestones(@PathVariable String containerId) {
        log.debug("GET /api/v1/tracking/containers/{}/milestones — fetching milestones", containerId);

        List<MilestoneResponse> responses = queryHandler.getMilestones(containerId)
                .stream()
                .map(MilestoneResponse::from)
                .toList();

        log.info("Retrieved {} milestones for containerId={}", responses.size(), containerId);

        return ResponseEntity.ok(responses);
    }

    /**
     * Updates the position of a container.
     *
     * @param containerId the ISO container identifier (path variable)
     * @param request     the position update request
     * @return 200 OK with the updated container
     */
    @PostMapping("/containers/{containerId}/position")
    public ResponseEntity<ContainerResponse> updatePosition(
            @PathVariable String containerId,
            @Valid @RequestBody UpdatePositionRequest request) {

        log.debug("POST /api/v1/tracking/containers/{}/position — updating position", containerId);

        Container container = commandHandler.updatePosition(containerId, request.toPosition());
        ContainerResponse response = ContainerResponse.from(container);

        log.info("Container position updated: containerId={}", containerId);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all containers for a booking.
     *
     * @param bookingId the booking UUID (path variable)
     * @return 200 OK with a list of containers (may be empty)
     */
    @GetMapping("/bookings/{bookingId}/containers")
    public ResponseEntity<List<ContainerResponse>> getContainersByBooking(
            @PathVariable UUID bookingId) {

        log.debug("GET /api/v1/tracking/bookings/{}/containers — fetching containers", bookingId);

        List<ContainerResponse> responses = queryHandler.getContainersByBooking(bookingId)
                .stream()
                .map(ContainerResponse::from)
                .toList();

        log.info("Retrieved {} container(s) for bookingId={}", responses.size(), bookingId);

        return ResponseEntity.ok(responses);
    }
}
