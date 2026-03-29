package com.freightflow.vesselschedule.domain.model;

import com.freightflow.vesselschedule.domain.event.CapacityReserved;
import com.freightflow.vesselschedule.domain.event.VesselEvent;
import com.freightflow.vesselschedule.domain.event.VoyageArrived;
import com.freightflow.vesselschedule.domain.event.VoyageDeparted;
import com.freightflow.commons.exception.ConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Voyage Aggregate Root — represents a scheduled vessel sailing between ports.
 *
 * <p>This class encapsulates all business rules for voyage management including
 * route schedules, capacity reservation, and voyage lifecycle (departure, arrival).</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Aggregate Root</b> (DDD) — single entry point for all voyage mutations</li>
 *   <li><b>Domain Events</b> — state changes emit events for downstream services</li>
 *   <li><b>Factory Method</b> — {@link #create} encapsulates creation logic</li>
 *   <li><b>Optimistic Locking</b> — concurrent capacity reservations are safe</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>Remaining capacity can never be negative</li>
 *   <li>A departed voyage cannot have capacity reserved</li>
 *   <li>Route must have at least two port calls</li>
 * </ul>
 *
 * @see Vessel
 * @see PortCall
 * @see VesselEvent
 */
public class Voyage {

    private final UUID voyageId;
    private final UUID vesselId;
    private final String voyageNumber;
    private final List<PortCall> route;
    private Instant createdAt;

    private VoyageStatus status;
    private int totalCapacityTeu;
    private int remainingCapacityTeu;
    private Instant updatedAt;
    private long version;

    /** Uncommitted domain events — cleared after publishing. */
    private final List<VesselEvent> domainEvents = new ArrayList<>();

    /**
     * Private constructor — use {@link #create} factory method.
     */
    private Voyage(UUID voyageId, UUID vesselId, String voyageNumber,
                   int totalCapacityTeu) {
        this.voyageId = Objects.requireNonNull(voyageId, "Voyage ID must not be null");
        this.vesselId = Objects.requireNonNull(vesselId, "Vessel ID must not be null");
        this.voyageNumber = Objects.requireNonNull(voyageNumber, "Voyage number must not be null");
        this.route = new ArrayList<>();
        this.totalCapacityTeu = totalCapacityTeu;
        this.remainingCapacityTeu = totalCapacityTeu;
        this.status = VoyageStatus.SCHEDULED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.version = 0;
    }

    // ==================== Factory Method ====================

    /**
     * Factory method to create a new voyage.
     *
     * @param vesselId         the vessel assigned to this voyage
     * @param voyageNumber     the voyage number (e.g., "VYG-2024-001")
     * @param totalCapacityTeu the total TEU capacity for this voyage
     * @param portCalls        the planned route (at least 2 port calls)
     * @return a new Voyage in SCHEDULED status
     * @throws IllegalArgumentException if capacity is not positive or route has fewer than 2 ports
     */
    public static Voyage create(UUID vesselId, String voyageNumber,
                                 int totalCapacityTeu, List<PortCall> portCalls) {
        if (totalCapacityTeu <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + totalCapacityTeu);
        }
        if (portCalls == null || portCalls.size() < 2) {
            throw new IllegalArgumentException("Route must have at least 2 port calls");
        }

        var voyage = new Voyage(UUID.randomUUID(), vesselId, voyageNumber, totalCapacityTeu);
        voyage.route.addAll(portCalls);
        return voyage;
    }

    // ==================== State Transitions ====================

    /**
     * Reserves capacity on this voyage for a booking.
     *
     * @param bookingId     the booking requesting capacity
     * @param teuRequired   the number of TEU to reserve
     * @throws ConflictException if insufficient capacity remains
     * @throws ConflictException if voyage has already departed
     */
    public void reserveCapacity(UUID bookingId, int teuRequired) {
        Objects.requireNonNull(bookingId, "Booking ID must not be null");
        if (teuRequired <= 0) {
            throw new IllegalArgumentException("TEU required must be positive, got: " + teuRequired);
        }
        if (this.status != VoyageStatus.SCHEDULED) {
            throw ConflictException.invalidStateTransition(
                    "Voyage", voyageId.toString(), status.name(), "RESERVE_CAPACITY");
        }
        if (teuRequired > remainingCapacityTeu) {
            throw new ConflictException(
                    "INSUFFICIENT_CAPACITY",
                    "Insufficient capacity on voyage %s: requested=%d, remaining=%d".formatted(
                            voyageId, teuRequired, remainingCapacityTeu),
                    status.name());
        }

        this.remainingCapacityTeu -= teuRequired;
        this.updatedAt = Instant.now();

        registerEvent(new CapacityReserved(
                this.voyageId, bookingId, teuRequired,
                this.remainingCapacityTeu, this.updatedAt
        ));
    }

    /**
     * Releases previously reserved capacity back to the voyage.
     *
     * @param teuToRelease the number of TEU to release
     */
    public void releaseCapacity(int teuToRelease) {
        if (teuToRelease <= 0) {
            throw new IllegalArgumentException("TEU to release must be positive");
        }

        this.remainingCapacityTeu = Math.min(
                this.remainingCapacityTeu + teuToRelease,
                this.totalCapacityTeu
        );
        this.updatedAt = Instant.now();
    }

    /**
     * Records voyage departure from the first port.
     *
     * @throws ConflictException if the voyage is not in SCHEDULED status
     */
    public void depart() {
        if (this.status != VoyageStatus.SCHEDULED) {
            throw ConflictException.invalidStateTransition(
                    "Voyage", voyageId.toString(), status.name(), "DEPARTED");
        }

        this.status = VoyageStatus.IN_TRANSIT;
        this.updatedAt = Instant.now();

        String departurePort = route.isEmpty() ? "UNKNOWN" : route.getFirst().port();
        registerEvent(new VoyageDeparted(
                this.voyageId, this.vesselId, departurePort, this.updatedAt
        ));
    }

    /**
     * Records voyage arrival at the final destination port.
     *
     * @throws ConflictException if the voyage is not in IN_TRANSIT status
     */
    public void arrive() {
        if (this.status != VoyageStatus.IN_TRANSIT) {
            throw ConflictException.invalidStateTransition(
                    "Voyage", voyageId.toString(), status.name(), "ARRIVED");
        }

        this.status = VoyageStatus.COMPLETED;
        this.updatedAt = Instant.now();

        String arrivalPort = route.isEmpty() ? "UNKNOWN" : route.getLast().port();
        registerEvent(new VoyageArrived(
                this.voyageId, this.vesselId, arrivalPort, this.updatedAt
        ));
    }

    // ==================== Domain Event Management ====================

    /**
     * Reconstitutes a Voyage from persisted state.
     *
     * @return a Voyage in its persisted state
     */
    public static Voyage reconstitute(UUID voyageId, UUID vesselId, String voyageNumber,
                                       VoyageStatus status, int totalCapacityTeu,
                                       int remainingCapacityTeu, List<PortCall> route,
                                       Instant createdAt, Instant updatedAt, long version) {
        var voyage = new Voyage(voyageId, vesselId, voyageNumber, totalCapacityTeu);
        voyage.status = status;
        voyage.remainingCapacityTeu = remainingCapacityTeu;
        voyage.route.addAll(route != null ? route : List.of());
        voyage.createdAt = createdAt;
        voyage.updatedAt = updatedAt;
        voyage.version = version;
        return voyage;
    }

    /**
     * Returns and clears all uncommitted domain events.
     *
     * @return an unmodifiable list of domain events
     */
    public List<VesselEvent> pullDomainEvents() {
        List<VesselEvent> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    private void registerEvent(VesselEvent event) {
        domainEvents.add(event);
    }

    // ==================== Accessors ====================

    public UUID getVoyageId() { return voyageId; }
    public UUID getVesselId() { return vesselId; }
    public String getVoyageNumber() { return voyageNumber; }
    public VoyageStatus getStatus() { return status; }
    public int getTotalCapacityTeu() { return totalCapacityTeu; }
    public int getRemainingCapacityTeu() { return remainingCapacityTeu; }
    public List<PortCall> getRoute() { return Collections.unmodifiableList(route); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    @Override
    public String toString() {
        return "Voyage[id=%s, number=%s, vessel=%s, status=%s, remaining=%d/%dTEU]".formatted(
                voyageId, voyageNumber, vesselId, status, remainingCapacityTeu, totalCapacityTeu);
    }

    /**
     * Voyage lifecycle status.
     */
    public enum VoyageStatus {
        /** Voyage is scheduled but has not departed. */
        SCHEDULED,
        /** Voyage is currently in transit. */
        IN_TRANSIT,
        /** Voyage has completed (arrived at final port). */
        COMPLETED,
        /** Voyage has been cancelled. */
        CANCELLED
    }
}
