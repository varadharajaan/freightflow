package com.freightflow.vesselschedule.infrastructure.adapter.in.rest;

import com.freightflow.vesselschedule.application.command.VesselCommandHandler;
import com.freightflow.vesselschedule.application.query.VesselQueryHandler;
import com.freightflow.vesselschedule.domain.model.Vessel;
import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.infrastructure.adapter.in.rest.dto.VesselResponse;
import com.freightflow.vesselschedule.infrastructure.adapter.in.rest.dto.VoyageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for vessel and voyage schedule operations.
 *
 * <p>This is the primary inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer commands and queries.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET  /api/v1/vessels}                     — list active vessels</li>
 *   <li>{@code GET  /api/v1/vessels/{id}}                — get vessel details</li>
 *   <li>{@code GET  /api/v1/voyages/{id}}                — get voyage details</li>
 *   <li>{@code GET  /api/v1/voyages}                     — list voyages by vessel</li>
 *   <li>{@code GET  /api/v1/routes/available}            — find voyages with capacity</li>
 *   <li>{@code POST /api/v1/voyages/{id}/depart}         — record voyage departure</li>
 *   <li>{@code POST /api/v1/voyages/{id}/arrive}         — record voyage arrival</li>
 * </ul>
 *
 * @see VesselCommandHandler
 * @see VesselQueryHandler
 */
@RestController
@RequestMapping("/api/v1")
public class VesselController {

    private static final Logger log = LoggerFactory.getLogger(VesselController.class);

    private final VesselQueryHandler queryHandler;
    private final VesselCommandHandler commandHandler;

    /**
     * Creates a new {@code VesselController} with the required handlers.
     *
     * @param queryHandler   the vessel query handler (must not be null)
     * @param commandHandler the vessel command handler (must not be null)
     */
    public VesselController(VesselQueryHandler queryHandler,
                            VesselCommandHandler commandHandler) {
        this.queryHandler = Objects.requireNonNull(queryHandler, "VesselQueryHandler must not be null");
        this.commandHandler = Objects.requireNonNull(commandHandler, "VesselCommandHandler must not be null");
    }

    /**
     * Lists all active vessels in the fleet.
     *
     * @return 200 OK with a list of active vessels
     */
    @GetMapping("/vessels")
    public ResponseEntity<List<VesselResponse>> getActiveVessels() {
        log.debug("GET /api/v1/vessels — listing active vessels");

        List<VesselResponse> responses = queryHandler.getActiveVessels()
                .stream()
                .map(VesselResponse::from)
                .toList();

        log.info("Retrieved {} active vessel(s)", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves a vessel by its unique identifier.
     *
     * @param vesselId the vessel UUID (path variable)
     * @return 200 OK with the vessel details
     */
    @GetMapping("/vessels/{vesselId}")
    public ResponseEntity<VesselResponse> getVessel(@PathVariable UUID vesselId) {
        log.debug("GET /api/v1/vessels/{} — fetching vessel", vesselId);

        Vessel vessel = queryHandler.getVessel(vesselId);
        VesselResponse response = VesselResponse.from(vessel);

        log.info("Vessel retrieved: vesselId={}, name={}", response.vesselId(), response.name());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a voyage by its unique identifier.
     *
     * @param voyageId the voyage UUID (path variable)
     * @return 200 OK with the voyage details
     */
    @GetMapping("/voyages/{voyageId}")
    public ResponseEntity<VoyageResponse> getVoyage(@PathVariable UUID voyageId) {
        log.debug("GET /api/v1/voyages/{} — fetching voyage", voyageId);

        Voyage voyage = queryHandler.getVoyage(voyageId);
        VoyageResponse response = VoyageResponse.from(voyage);

        log.info("Voyage retrieved: voyageId={}, status={}", response.voyageId(), response.status());

        return ResponseEntity.ok(response);
    }

    /**
     * Lists voyages for a vessel.
     *
     * @param vesselId the vessel UUID (query parameter)
     * @return 200 OK with a list of voyages
     */
    @GetMapping("/voyages")
    public ResponseEntity<List<VoyageResponse>> getVoyagesByVessel(
            @RequestParam UUID vesselId) {

        log.debug("GET /api/v1/voyages?vesselId={} — listing voyages", vesselId);

        List<VoyageResponse> responses = queryHandler.getVoyagesByVessel(vesselId)
                .stream()
                .map(VoyageResponse::from)
                .toList();

        log.info("Retrieved {} voyage(s) for vesselId={}", responses.size(), vesselId);

        return ResponseEntity.ok(responses);
    }

    /**
     * Finds scheduled voyages with available capacity.
     *
     * @param minCapacity the minimum required TEU capacity (query parameter, default 1)
     * @return 200 OK with a list of available voyages
     */
    @GetMapping("/routes/available")
    public ResponseEntity<List<VoyageResponse>> findAvailableVoyages(
            @RequestParam(defaultValue = "1") int minCapacity) {

        log.debug("GET /api/v1/routes/available?minCapacity={} — finding available voyages", minCapacity);

        List<VoyageResponse> responses = queryHandler.findAvailableVoyages(minCapacity)
                .stream()
                .map(VoyageResponse::from)
                .toList();

        log.info("Found {} available voyage(s) with capacity >= {} TEU", responses.size(), minCapacity);

        return ResponseEntity.ok(responses);
    }

    /**
     * Records a voyage departure.
     *
     * @param voyageId the voyage UUID (path variable)
     * @return 200 OK with the updated voyage
     */
    @PostMapping("/voyages/{voyageId}/depart")
    public ResponseEntity<VoyageResponse> departVoyage(@PathVariable UUID voyageId) {
        log.debug("POST /api/v1/voyages/{}/depart — recording departure", voyageId);

        Voyage voyage = commandHandler.departVoyage(voyageId);
        VoyageResponse response = VoyageResponse.from(voyage);

        log.info("Voyage departed: voyageId={}", voyageId);

        return ResponseEntity.ok(response);
    }

    /**
     * Records a voyage arrival.
     *
     * @param voyageId the voyage UUID (path variable)
     * @return 200 OK with the updated voyage
     */
    @PostMapping("/voyages/{voyageId}/arrive")
    public ResponseEntity<VoyageResponse> arriveVoyage(@PathVariable UUID voyageId) {
        log.debug("POST /api/v1/voyages/{}/arrive — recording arrival", voyageId);

        Voyage voyage = commandHandler.arriveVoyage(voyageId);
        VoyageResponse response = VoyageResponse.from(voyage);

        log.info("Voyage arrived: voyageId={}", voyageId);

        return ResponseEntity.ok(response);
    }
}
