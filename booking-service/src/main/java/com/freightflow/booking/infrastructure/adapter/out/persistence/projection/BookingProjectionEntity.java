package com.freightflow.booking.infrastructure.adapter.out.persistence.projection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code booking_projections} table (V3 migration).
 *
 * <p>Represents the denormalized read model for a booking, materialized from domain
 * events by the {@link BookingProjectionUpdater}. This entity is optimized for query
 * performance — flat structure, no joins, all frequently accessed fields in a single row.</p>
 *
 * <p>The {@code last_event_version} field is used for idempotent projection updates:
 * events with a version &le; the current version are skipped as already applied.</p>
 *
 * @see BookingProjectionUpdater
 * @see SpringDataProjectionRepository
 */
@Entity
@Table(name = "booking_projections")
public class BookingProjectionEntity {

    /** Same ID as the booking aggregate. */
    @Id
    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    /** The customer who placed the booking. */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Current booking status (DRAFT, CONFIRMED, CANCELLED). */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** Origin port code. */
    @Column(name = "origin_port", nullable = false, length = 10)
    private String originPort;

    /** Destination port code. */
    @Column(name = "destination_port", nullable = false, length = 10)
    private String destinationPort;

    /** Type of container requested. */
    @Column(name = "container_type", nullable = false, length = 20)
    private String containerType;

    /** Number of containers. */
    @Column(name = "container_count", nullable = false)
    private int containerCount;

    /** Commodity description. */
    @Column(name = "commodity_description", length = 500)
    private String commodityDescription;

    /** Assigned voyage ID (set on confirmation). */
    @Column(name = "voyage_id")
    private UUID voyageId;

    /** Cancellation reason (set on cancellation). */
    @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

    /** Desired departure date. */
    @Column(name = "requested_departure_date", nullable = false)
    private LocalDate requestedDepartureDate;

    /** Version of the last event applied to this projection. */
    @Column(name = "last_event_version", nullable = false)
    private long lastEventVersion;

    /** When the booking was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When the projection was last updated. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Auto-incrementing sequence for cursor-based pagination. */
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_number", nullable = false, insertable = false, updatable = false)
    private long sequenceNumber;

    /** Protected no-arg constructor required by JPA. */
    protected BookingProjectionEntity() {
        // JPA requires a no-arg constructor
    }

    /**
     * Constructs a new booking projection entity (used on initial creation from BookingCreated).
     *
     * @param bookingId              the booking aggregate ID
     * @param customerId             the customer ID
     * @param status                 the initial status
     * @param originPort             origin port code
     * @param destinationPort        destination port code
     * @param containerType          container type
     * @param containerCount         number of containers
     * @param commodityDescription   commodity description
     * @param requestedDepartureDate desired departure date
     * @param lastEventVersion       the version of the event that created this projection
     * @param createdAt              when the booking was created
     */
    public BookingProjectionEntity(UUID bookingId, UUID customerId, String status,
                                   String originPort, String destinationPort,
                                   String containerType, int containerCount,
                                   String commodityDescription,
                                   LocalDate requestedDepartureDate,
                                   long lastEventVersion, Instant createdAt) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.status = status;
        this.originPort = originPort;
        this.destinationPort = destinationPort;
        this.containerType = containerType;
        this.containerCount = containerCount;
        this.commodityDescription = commodityDescription;
        this.requestedDepartureDate = requestedDepartureDate;
        this.lastEventVersion = lastEventVersion;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    // -- Getters --

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getStatus() {
        return status;
    }

    public String getOriginPort() {
        return originPort;
    }

    public String getDestinationPort() {
        return destinationPort;
    }

    public String getContainerType() {
        return containerType;
    }

    public int getContainerCount() {
        return containerCount;
    }

    public String getCommodityDescription() {
        return commodityDescription;
    }

    public UUID getVoyageId() {
        return voyageId;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public LocalDate getRequestedDepartureDate() {
        return requestedDepartureDate;
    }

    public long getLastEventVersion() {
        return lastEventVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    // -- Mutation methods for projection updates --

    /**
     * Updates the projection status and voyage ID (used on BookingConfirmed).
     *
     * @param status       the new status
     * @param voyageId     the assigned voyage ID
     * @param eventVersion the version of the event causing this update
     */
    public void confirmBooking(String status, UUID voyageId, long eventVersion) {
        this.status = status;
        this.voyageId = voyageId;
        this.lastEventVersion = eventVersion;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the projection status and cancellation reason (used on BookingCancelled).
     *
     * @param status             the new status
     * @param cancellationReason the reason for cancellation
     * @param eventVersion       the version of the event causing this update
     */
    public void cancelBooking(String status, String cancellationReason, long eventVersion) {
        this.status = status;
        this.cancellationReason = cancellationReason;
        this.lastEventVersion = eventVersion;
        this.updatedAt = Instant.now();
    }
}
