package com.freightflow.booking.domain.model;

import com.freightflow.booking.domain.event.BookingCancelled;
import com.freightflow.booking.domain.event.BookingConfirmed;
import com.freightflow.booking.domain.event.BookingCreated;
import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.VoyageId;
import com.freightflow.commons.exception.ConflictException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Booking Aggregate Root — the central domain entity managing the booking lifecycle.
 *
 * <p>This class encapsulates all business rules and invariants for a cargo booking.
 * State transitions are enforced via the {@link BookingStatus} state machine.
 * Every state change produces a domain event that is collected for later publishing.</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Aggregate Root</b> (DDD) — single entry point for all booking mutations</li>
 *   <li><b>State Pattern</b> — transitions governed by {@link BookingStatus#canTransitionTo}</li>
 *   <li><b>Domain Events</b> — state changes emit events for downstream services</li>
 *   <li><b>Factory Method</b> — {@link #create} encapsulates creation logic</li>
 *   <li><b>Tell, Don't Ask</b> — methods mutate state internally, callers don't set fields</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>A booking always has an ID, customer, cargo, and requested departure date</li>
 *   <li>State transitions follow the state machine (see {@link BookingStatus})</li>
 *   <li>A confirmed booking must have a voyage assigned</li>
 *   <li>A cancelled booking cannot be modified further</li>
 * </ul>
 *
 * @see BookingStatus
 * @see BookingEvent
 */
public class Booking {

    private final BookingId id;
    private final CustomerId customerId;
    private final Cargo cargo;
    private final LocalDate requestedDepartureDate;
    private final Instant createdAt;

    private BookingStatus status;
    private VoyageId voyageId;
    private String cancellationReason;
    private Instant updatedAt;
    private long version;

    /** Uncommitted domain events — cleared after publishing. */
    private final List<BookingEvent> domainEvents = new ArrayList<>();

    /**
     * Private constructor — use {@link #create} factory method.
     * Enforces that all bookings go through proper validation (Open/Closed Principle).
     */
    private Booking(BookingId id, CustomerId customerId, Cargo cargo,
                    LocalDate requestedDepartureDate) {
        this.id = Objects.requireNonNull(id, "Booking ID must not be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID must not be null");
        this.cargo = Objects.requireNonNull(cargo, "Cargo must not be null");
        this.requestedDepartureDate = Objects.requireNonNull(requestedDepartureDate,
                "Requested departure date must not be null");
        this.status = BookingStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.version = 0;
    }

    // ==================== Factory Method ====================

    /**
     * Factory method to create a new booking in DRAFT status.
     *
     * <p>Validates all inputs and produces a {@link BookingCreated} domain event.
     * The booking is not persisted until the calling use case saves it.</p>
     *
     * @param customerId              the customer placing the booking
     * @param cargo                   the cargo details
     * @param requestedDepartureDate  the desired departure date
     * @return a new Booking in DRAFT status
     * @throws IllegalArgumentException if departure date is in the past
     */
    public static Booking create(CustomerId customerId, Cargo cargo,
                                  LocalDate requestedDepartureDate) {
        if (requestedDepartureDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Requested departure date must be in the future, got: " + requestedDepartureDate);
        }

        var booking = new Booking(BookingId.generate(), customerId, cargo, requestedDepartureDate);

        booking.registerEvent(new BookingCreated(
                booking.id,
                booking.customerId,
                booking.cargo.origin(),
                booking.cargo.destination(),
                booking.cargo.containerType(),
                booking.cargo.containerCount(),
                booking.requestedDepartureDate,
                booking.createdAt
        ));

        return booking;
    }

    // ==================== State Transitions (Tell, Don't Ask) ====================

    /**
     * Confirms the booking and assigns it to a voyage.
     *
     * <p>Transition: DRAFT → CONFIRMED</p>
     *
     * @param voyageId the voyage this booking is assigned to
     * @throws ConflictException if the booking is not in DRAFT status
     */
    public void confirm(VoyageId voyageId) {
        Objects.requireNonNull(voyageId, "Voyage ID must not be null for confirmation");
        assertTransition(BookingStatus.CONFIRMED);

        this.status = BookingStatus.CONFIRMED;
        this.voyageId = voyageId;
        this.updatedAt = Instant.now();

        registerEvent(new BookingConfirmed(
                this.id, this.customerId, voyageId, this.updatedAt
        ));
    }

    /**
     * Cancels the booking with a reason.
     *
     * <p>Transition: DRAFT → CANCELLED or CONFIRMED → CANCELLED</p>
     *
     * @param reason the cancellation reason
     * @throws ConflictException if the booking cannot be cancelled from its current state
     */
    public void cancel(String reason) {
        Objects.requireNonNull(reason, "Cancellation reason must not be null");
        assertTransition(BookingStatus.CANCELLED);

        BookingStatus previousStatus = this.status;
        this.status = BookingStatus.CANCELLED;
        this.cancellationReason = reason;
        this.updatedAt = Instant.now();

        registerEvent(new BookingCancelled(
                this.id, this.customerId, previousStatus, reason, this.updatedAt
        ));
    }

    /**
     * Marks the booking as shipped (cargo loaded onto vessel).
     *
     * <p>Transition: CONFIRMED → SHIPPED</p>
     *
     * @throws ConflictException if the booking is not in CONFIRMED status
     */
    public void markShipped() {
        assertTransition(BookingStatus.SHIPPED);
        this.status = BookingStatus.SHIPPED;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the booking as delivered (cargo arrived at destination).
     *
     * <p>Transition: SHIPPED → DELIVERED</p>
     *
     * @throws ConflictException if the booking is not in SHIPPED status
     */
    public void markDelivered() {
        assertTransition(BookingStatus.DELIVERED);
        this.status = BookingStatus.DELIVERED;
        this.updatedAt = Instant.now();
    }

    // ==================== Domain Event Management ====================

    /**
     * Returns and clears all uncommitted domain events.
     *
     * <p>This method is called by the infrastructure layer after persisting the aggregate,
     * to publish events to Kafka. Events are cleared to prevent duplicate publishing.</p>
     *
     * @return an unmodifiable list of domain events
     */
    public List<BookingEvent> pullDomainEvents() {
        List<BookingEvent> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    private void registerEvent(BookingEvent event) {
        domainEvents.add(event);
    }

    // ==================== State Transition Guard ====================

    /**
     * Validates that a state transition is allowed; throws ConflictException if not.
     *
     * @param target the desired target state
     * @throws ConflictException if the transition is not valid
     */
    private void assertTransition(BookingStatus target) {
        if (!status.canTransitionTo(target)) {
            throw ConflictException.invalidStateTransition(
                    "Booking", id.asString(), status.name(), target.name());
        }
    }

    // ==================== Accessors (No Setters — Immutable from outside) ====================

    public BookingId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public Cargo getCargo() {
        return cargo;
    }

    public LocalDate getRequestedDepartureDate() {
        return requestedDepartureDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Optional<VoyageId> getVoyageId() {
        return Optional.ofNullable(voyageId);
    }

    public Optional<String> getCancellationReason() {
        return Optional.ofNullable(cancellationReason);
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
        return "Booking[id=%s, status=%s, customer=%s, route=%s→%s]".formatted(
                id.asString(), status,
                customerId.asString(),
                cargo.origin().value(),
                cargo.destination().value());
    }
}
