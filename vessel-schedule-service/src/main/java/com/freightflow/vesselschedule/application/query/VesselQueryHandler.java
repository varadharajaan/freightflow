package com.freightflow.vesselschedule.application.query;

import com.freightflow.vesselschedule.domain.model.Vessel;
import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.domain.port.VesselRepository;
import com.freightflow.vesselschedule.domain.port.VoyageRepository;
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
 * CQRS Query Handler — handles all <b>read</b> operations for the vessel schedule domain.
 *
 * <p>This is the <b>read side</b> of CQRS. It queries vessel and voyage data with
 * heavy caching for schedule lookups. Schedule data changes infrequently and is
 * queried heavily, making it an ideal candidate for caching.</p>
 *
 * <h3>Caching Strategy</h3>
 * <p>Vessel and voyage schedule data is cached using Caffeine for in-process caching.
 * Cache TTLs are configured in {@code application.yml}. Cache is evicted when
 * write operations modify the underlying data.</p>
 *
 * @see com.freightflow.vesselschedule.application.command.VesselCommandHandler
 */
@Service
public class VesselQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(VesselQueryHandler.class);

    private final VesselRepository vesselRepository;
    private final VoyageRepository voyageRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param vesselRepository the domain port for vessel persistence
     * @param voyageRepository the domain port for voyage persistence
     */
    public VesselQueryHandler(VesselRepository vesselRepository,
                              VoyageRepository voyageRepository) {
        this.vesselRepository = Objects.requireNonNull(vesselRepository,
                "vesselRepository must not be null");
        this.voyageRepository = Objects.requireNonNull(voyageRepository,
                "voyageRepository must not be null");
    }

    /**
     * Retrieves a vessel by its ID.
     *
     * <h3>Spring Advanced Feature: @Cacheable</h3>
     * <p>Vessel data is heavily cached since it changes infrequently.</p>
     *
     * @param vesselId the vessel identifier
     * @return the vessel
     * @throws ResourceNotFoundException if no vessel exists for the given ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "vessels", key = "#vesselId", unless = "#result == null")
    @Profiled(value = "getVessel", slowThresholdMs = 200)
    public Vessel getVessel(UUID vesselId) {
        log.debug("Querying vessel: vesselId={}", vesselId);

        return vesselRepository.findById(vesselId)
                .orElseThrow(() -> {
                    log.warn("Vessel not found: vesselId={}", vesselId);
                    return new ResourceNotFoundException("Vessel", vesselId.toString());
                });
    }

    /**
     * Retrieves all active vessels in the fleet.
     *
     * @return list of active vessels (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "active-vessels", unless = "#result.isEmpty()")
    @Profiled(value = "getActiveVessels", slowThresholdMs = 300)
    public List<Vessel> getActiveVessels() {
        log.debug("Querying all active vessels");

        List<Vessel> vessels = vesselRepository.findAllActive();
        log.debug("Found {} active vessel(s)", vessels.size());

        return vessels;
    }

    /**
     * Retrieves a voyage by its ID.
     *
     * @param voyageId the voyage identifier
     * @return the voyage
     * @throws ResourceNotFoundException if no voyage exists for the given ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "voyages", key = "#voyageId", unless = "#result == null")
    @Profiled(value = "getVoyage", slowThresholdMs = 200)
    public Voyage getVoyage(UUID voyageId) {
        log.debug("Querying voyage: voyageId={}", voyageId);

        return voyageRepository.findById(voyageId)
                .orElseThrow(() -> {
                    log.warn("Voyage not found: voyageId={}", voyageId);
                    return new ResourceNotFoundException("Voyage", voyageId.toString());
                });
    }

    /**
     * Retrieves all voyages for a vessel.
     *
     * @param vesselId the vessel identifier
     * @return list of voyages for the vessel
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "vessel-voyages", key = "#vesselId", unless = "#result.isEmpty()")
    @Profiled(value = "getVoyagesByVessel", slowThresholdMs = 300)
    public List<Voyage> getVoyagesByVessel(UUID vesselId) {
        log.debug("Querying voyages for vessel: vesselId={}", vesselId);

        List<Voyage> voyages = voyageRepository.findByVesselId(vesselId);
        log.debug("Found {} voyage(s) for vessel: vesselId={}", voyages.size(), vesselId);

        return voyages;
    }

    /**
     * Finds scheduled voyages with available capacity.
     *
     * @param minCapacityTeu the minimum required TEU capacity
     * @return list of voyages with sufficient capacity
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "available-routes", key = "#minCapacityTeu", unless = "#result.isEmpty()")
    @Profiled(value = "findAvailableVoyages", slowThresholdMs = 500)
    public List<Voyage> findAvailableVoyages(int minCapacityTeu) {
        log.debug("Finding voyages with capacity >= {} TEU", minCapacityTeu);

        List<Voyage> voyages = voyageRepository.findScheduledWithCapacity(minCapacityTeu);
        log.debug("Found {} voyage(s) with capacity >= {} TEU", voyages.size(), minCapacityTeu);

        return voyages;
    }
}
