package com.freightflow.trackingservice.domain.model;

import com.freightflow.trackingservice.domain.event.ContainerDelivered;
import com.freightflow.trackingservice.domain.event.ContainerMilestoneReached;
import com.freightflow.trackingservice.domain.event.ContainerPositionUpdated;
import com.freightflow.trackingservice.domain.event.TrackingEvent;
import com.freightflow.commons.exception.ConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Container Aggregate Root — the central domain entity managing container tracking lifecycle.
 *
 * <p>This class encapsulates all business rules and invariants for container tracking.
 * State transitions are enforced via the {@link ContainerStatus} state machine.
 * Every state change produces a domain event that is collected for later publishing.</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Aggregate Root</b> (DDD) — single entry point for all container mutations</li>
 *   <li><b>State Pattern</b> — transitions governed by {@link ContainerStatus#canTransitionTo}</li>
 *   <li><b>Domain Events</b> — state changes emit events for downstream services</li>
 *   <li><b>Factory Method</b> — {@link #create} encapsulates creation logic</li>
 *   <li><b>Tell, Don't Ask</b> — methods mutate state internally, callers don't set fields</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>A container always has an ID (ISO format) and a booking reference</li>
 *   <li>State transitions follow the state machine (see {@link ContainerStatus})</li>
 *   <li>A delivered container cannot be modified further</li>
 *   <li>Position updates are recorded with source and timestamp</li>
 * </ul>
 *
 * @see ContainerStatus
 * @see TrackingEvent
 */
public class Container {

    private final String containerId;
    private final UUID bookingId;
    private final List<Milestone> milestones;
    private Instant createdAt;

    private ContainerStatus status;
    private Position currentPosition;
    private UUID voyageId;
    private Instant updatedAt;
    private long version;

    /** Uncommitted domain events — cleared after publishing. */
    private final List<TrackingEvent> domainEvents = new ArrayList<>();

    /**
     * Private constructor — use {@link #create} factory method.
     * Enforces that all containers go through proper validation.
     */
    private Container(String containerId, UUID bookingId) {
        this.containerId = Objects.requireNonNull(containerId, "Container ID must not be null");
        this.bookingId = Objects.requireNonNull(bookingId, "Booking ID must not be null");
        this.milestones = new ArrayList<>();
        this.status = ContainerStatus.EMPTY;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.version = 0;
    }

    // ==================== Factory Method ====================

    /**
     * Factory method to create a new container tracking record in EMPTY status.
     *
     * <p>Validates all inputs. The container is not persisted until the calling
     * use case saves it.</p>
     *
     * @param containerId the ISO container identifier (e.g., MSCU1234567)
     * @param bookingId   the booking this container belongs to
     * @return a new Container in EMPTY status
     * @throws IllegalArgumentException if containerId is blank
     */
    public static Container create(String containerId, UUID bookingId) {
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalArgumentException("Container ID must not be blank");
        }

        return new Container(containerId, bookingId);
    }

    // ==================== State Transitions (Tell, Don't Ask) ====================

    /**
     * Updates the current position of the container.
     *
     * <p>Position updates are allowed in any non-terminal state. Produces a
     * {@link ContainerPositionUpdated} domain event.</p>
     *
     * @param position the new position reading
     * @throws IllegalArgumentException if position is null
     */
    public void updatePosition(Position position) {
        Objects.requireNonNull(position, "Position must not be null");
        if (this.status == ContainerStatus.DELIVERED) {
            throw ConflictException.invalidStateTransition(
                    "Container", containerId, status.name(), "POSITION_UPDATE");
        }

        this.currentPosition = position;
        this.updatedAt = Instant.now();

        registerEvent(new ContainerPositionUpdated(
                this.containerId, this.currentPosition, this.updatedAt
        ));
    }

    /**
     * Loads cargo into the container.
     *
     * <p>Transition: EMPTY → LOADED</p>
     *
     * @throws ConflictException if the container is not in EMPTY status
     */
    public void loadCargo() {
        assertTransition(ContainerStatus.LOADED);

        this.status = ContainerStatus.LOADED;
        this.updatedAt = Instant.now();

        var milestone = new Milestone(
                Milestone.MilestoneType.LOADED,
                currentPortOrUnknown(),
                this.updatedAt,
                "Cargo loaded into container " + containerId
        );
        this.milestones.add(milestone);

        registerEvent(new ContainerMilestoneReached(this.containerId, milestone));
    }

    /**
     * Marks the container as in transit on a voyage.
     *
     * <p>Transition: LOADED → IN_TRANSIT or AT_PORT → IN_TRANSIT (transshipment)</p>
     *
     * @param voyageId the voyage this container is traveling on
     * @throws ConflictException if the transition is not valid
     */
    public void depart(UUID voyageId) {
        Objects.requireNonNull(voyageId, "Voyage ID must not be null for departure");
        assertTransition(ContainerStatus.IN_TRANSIT);

        this.status = ContainerStatus.IN_TRANSIT;
        this.voyageId = voyageId;
        this.updatedAt = Instant.now();

        var milestone = new Milestone(
                Milestone.MilestoneType.DEPARTED,
                currentPortOrUnknown(),
                this.updatedAt,
                "Container " + containerId + " departed on voyage " + voyageId
        );
        this.milestones.add(milestone);

        registerEvent(new ContainerMilestoneReached(this.containerId, milestone));
    }

    /**
     * Unloads cargo — container arrives at a port.
     *
     * <p>Transition: IN_TRANSIT → AT_PORT</p>
     *
     * @param port the port code where the container arrived
     * @throws ConflictException if the container is not in IN_TRANSIT status
     */
    public void unloadCargo(String port) {
        Objects.requireNonNull(port, "Port must not be null");
        assertTransition(ContainerStatus.AT_PORT);

        this.status = ContainerStatus.AT_PORT;
        this.updatedAt = Instant.now();

        var milestone = new Milestone(
                Milestone.MilestoneType.ARRIVED,
                port,
                this.updatedAt,
                "Container " + containerId + " arrived at port " + port
        );
        this.milestones.add(milestone);

        registerEvent(new ContainerMilestoneReached(this.containerId, milestone));
    }

    /**
     * Marks the container as delivered to the consignee.
     *
     * <p>Transition: AT_PORT → DELIVERED</p>
     *
     * @throws ConflictException if the container is not in AT_PORT status
     */
    public void markDelivered() {
        assertTransition(ContainerStatus.DELIVERED);

        this.status = ContainerStatus.DELIVERED;
        this.updatedAt = Instant.now();

        var milestone = new Milestone(
                Milestone.MilestoneType.GATE_OUT,
                currentPortOrUnknown(),
                this.updatedAt,
                "Container " + containerId + " delivered to consignee"
        );
        this.milestones.add(milestone);

        registerEvent(new ContainerDelivered(this.containerId, this.updatedAt));
    }

    // ==================== Domain Event Management ====================

    /**
     * Reconstitutes a Container aggregate from persisted state.
     *
     * <p>This method is used ONLY by the persistence adapter when loading a container
     * from the database. No domain events are emitted — this is state reconstruction.</p>
     *
     * @return a Container aggregate in its persisted state
     */
    public static Container reconstitute(String containerId, UUID bookingId,
                                          ContainerStatus status, Position currentPosition,
                                          UUID voyageId, List<Milestone> milestones,
                                          Instant createdAt, Instant updatedAt, long version) {
        var container = new Container(containerId, bookingId);
        container.status = status;
        container.currentPosition = currentPosition;
        container.voyageId = voyageId;
        container.milestones.addAll(milestones != null ? milestones : List.of());
        container.createdAt = createdAt;
        container.updatedAt = updatedAt;
        container.version = version;
        return container;
    }

    /**
     * Returns and clears all uncommitted domain events.
     *
     * <p>This method is called by the infrastructure layer after persisting the aggregate,
     * to publish events to Kafka. Events are cleared to prevent duplicate publishing.</p>
     *
     * @return an unmodifiable list of domain events
     */
    public List<TrackingEvent> pullDomainEvents() {
        List<TrackingEvent> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    private void registerEvent(TrackingEvent event) {
        domainEvents.add(event);
    }

    // ==================== State Transition Guard ====================

    /**
     * Validates that a state transition is allowed; throws ConflictException if not.
     *
     * @param target the desired target state
     * @throws ConflictException if the transition is not valid
     */
    private void assertTransition(ContainerStatus target) {
        if (!status.canTransitionTo(target)) {
            throw ConflictException.invalidStateTransition(
                    "Container", containerId, status.name(), target.name());
        }
    }

    private String currentPortOrUnknown() {
        if (!milestones.isEmpty()) {
            return milestones.getLast().port();
        }
        return "UNKNOWN";
    }

    // ==================== Accessors (No Setters — Immutable from outside) ====================

    public String getContainerId() {
        return containerId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public ContainerStatus getStatus() {
        return status;
    }

    public Optional<Position> getCurrentPosition() {
        return Optional.ofNullable(currentPosition);
    }

    public Optional<UUID> getVoyageId() {
        return Optional.ofNullable(voyageId);
    }

    public List<Milestone> getMilestones() {
        return Collections.unmodifiableList(milestones);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Container[id=%s, status=%s, bookingId=%s]".formatted(
                containerId, status, bookingId);
    }
}
