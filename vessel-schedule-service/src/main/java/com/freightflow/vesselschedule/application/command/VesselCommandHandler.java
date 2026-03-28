package com.freightflow.vesselschedule.application.command;

import com.freightflow.vesselschedule.domain.event.VesselEvent;
import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.domain.port.VoyageRepository;
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
 * CQRS Command Handler — handles all write operations for vessel and voyage aggregates.
 *
 * <p>This is the <b>write side</b> of CQRS. It handles capacity reservation triggered
 * by booking confirmation events, voyage departure, and voyage arrival recording.</p>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Command Handler</b> — each method handles a specific vessel/voyage intention</li>
 *   <li><b>CQRS</b> — write operations separated from read operations</li>
 *   <li><b>Optimistic Locking</b> — concurrent capacity reservations are safe</li>
 * </ul>
 *
 * @see com.freightflow.vesselschedule.application.query.VesselQueryHandler
 */
@Service
public class VesselCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(VesselCommandHandler.class);

    private final VoyageRepository voyageRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param voyageRepository the domain port for voyage persistence
     */
    public VesselCommandHandler(VoyageRepository voyageRepository) {
        this.voyageRepository = Objects.requireNonNull(voyageRepository,
                "voyageRepository must not be null");
    }

    /**
     * Reserves capacity on a voyage for a booking.
     *
     * <p>Called by the Kafka consumer when a {@code BookingConfirmed} event is received.
     * Uses optimistic locking to handle concurrent reservations.</p>
     *
     * @param voyageId    the voyage to reserve capacity on
     * @param bookingId   the booking requesting capacity
     * @param teuRequired the number of TEU to reserve
     * @return the updated voyage
     * @throws ResourceNotFoundException if the voyage is not found
     */
    @Transactional
    @Profiled(value = "reserveVoyageCapacity", slowThresholdMs = 500)
    public Voyage reserveCapacity(UUID voyageId, UUID bookingId, int teuRequired) {
        log.debug("Reserving capacity: voyageId={}, bookingId={}, teu={}",
                voyageId, bookingId, teuRequired);

        Voyage voyage = loadVoyageOrThrow(voyageId);
        voyage.reserveCapacity(bookingId, teuRequired);

        Voyage saved = voyageRepository.save(voyage);
        List<VesselEvent> events = saved.pullDomainEvents();

        log.info("Capacity reserved: voyageId={}, bookingId={}, teu={}, remaining={}",
                voyageId, bookingId, teuRequired, saved.getRemainingCapacityTeu());

        return saved;
    }

    /**
     * Releases previously reserved capacity back to a voyage.
     *
     * @param voyageId     the voyage to release capacity on
     * @param teuToRelease the number of TEU to release
     * @return the updated voyage
     * @throws ResourceNotFoundException if the voyage is not found
     */
    @Transactional
    @Profiled(value = "releaseVoyageCapacity", slowThresholdMs = 500)
    public Voyage releaseCapacity(UUID voyageId, int teuToRelease) {
        log.debug("Releasing capacity: voyageId={}, teu={}", voyageId, teuToRelease);

        Voyage voyage = loadVoyageOrThrow(voyageId);
        voyage.releaseCapacity(teuToRelease);

        Voyage saved = voyageRepository.save(voyage);

        log.info("Capacity released: voyageId={}, released={}, remaining={}",
                voyageId, teuToRelease, saved.getRemainingCapacityTeu());

        return saved;
    }

    /**
     * Records a voyage departure.
     *
     * @param voyageId the voyage that is departing
     * @return the updated voyage
     * @throws ResourceNotFoundException if the voyage is not found
     */
    @Transactional
    @Profiled(value = "departVoyage", slowThresholdMs = 500)
    public Voyage departVoyage(UUID voyageId) {
        log.debug("Recording voyage departure: voyageId={}", voyageId);

        Voyage voyage = loadVoyageOrThrow(voyageId);
        voyage.depart();

        Voyage saved = voyageRepository.save(voyage);
        List<VesselEvent> events = saved.pullDomainEvents();

        log.info("Voyage departed: voyageId={}, vessel={}, status={}",
                voyageId, saved.getVesselId(), saved.getStatus());

        return saved;
    }

    /**
     * Records a voyage arrival at the final destination.
     *
     * @param voyageId the voyage that has arrived
     * @return the updated voyage
     * @throws ResourceNotFoundException if the voyage is not found
     */
    @Transactional
    @Profiled(value = "arriveVoyage", slowThresholdMs = 500)
    public Voyage arriveVoyage(UUID voyageId) {
        log.debug("Recording voyage arrival: voyageId={}", voyageId);

        Voyage voyage = loadVoyageOrThrow(voyageId);
        voyage.arrive();

        Voyage saved = voyageRepository.save(voyage);
        List<VesselEvent> events = saved.pullDomainEvents();

        log.info("Voyage arrived: voyageId={}, vessel={}, status={}",
                voyageId, saved.getVesselId(), saved.getStatus());

        return saved;
    }

    private Voyage loadVoyageOrThrow(UUID voyageId) {
        return voyageRepository.findById(voyageId)
                .orElseThrow(() -> {
                    log.warn("Voyage not found: voyageId={}", voyageId);
                    return new ResourceNotFoundException("Voyage", voyageId.toString());
                });
    }
}
