package com.freightflow.booking.infrastructure.adapter.out.persistence.eventstore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code booking_events} table (V3 migration).
 *
 * <p>Represents a single persisted domain event in the append-only event store.
 * This entity is the infrastructure-layer representation — it maps JSONB columns
 * to {@code String} fields that are serialized/deserialized by the adapter.</p>
 *
 * <p>The {@code aggregate_id + version} unique constraint provides optimistic
 * concurrency control: concurrent writes to the same aggregate at the same
 * version will fail with a constraint violation.</p>
 *
 * @see JpaEventStoreAdapter
 * @see SpringDataEventStoreRepository
 */
@Entity
@Table(name = "booking_events")
public class EventStoreEntity {

    /** Unique event identifier (UUID primary key). */
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    /** The aggregate this event belongs to. */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** Aggregate type for multi-aggregate event stores (default: "Booking"). */
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
    private String aggregateType;

    /** Event type name (e.g., "BookingCreated", "BookingConfirmed"). */
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    /** Event payload as JSONB — the actual event data. */
    @Column(name = "event_data", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String eventData;

    /** Metadata as JSONB (correlation ID, user ID, source service). */
    @Column(name = "metadata", updatable = false, columnDefinition = "jsonb")
    private String metadata;

    /** Monotonically increasing version per aggregate (for ordering + concurrency). */
    @Column(name = "version", nullable = false, updatable = false)
    private long version;

    /** When this event occurred in the domain. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** When this event was persisted to the store. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Who/what created this event. */
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    /** Protected no-arg constructor required by JPA. */
    protected EventStoreEntity() {
        // JPA requires a no-arg constructor
    }

    /**
     * Constructs a fully populated event store entity.
     *
     * @param eventId       unique event identifier
     * @param aggregateId   the aggregate this event belongs to
     * @param aggregateType the aggregate type name
     * @param eventType     the event type name
     * @param eventData     the serialized event payload (JSON)
     * @param metadata      the serialized metadata (JSON, nullable)
     * @param version       the aggregate version
     * @param occurredAt    when the event occurred in the domain
     * @param createdAt     when the event was persisted
     * @param createdBy     who/what created the event (nullable)
     */
    public EventStoreEntity(UUID eventId, UUID aggregateId, String aggregateType,
                            String eventType, String eventData, String metadata,
                            long version, Instant occurredAt, Instant createdAt,
                            String createdBy) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.eventData = eventData;
        this.metadata = metadata;
        this.version = version;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventData() {
        return eventData;
    }

    public String getMetadata() {
        return metadata;
    }

    public long getVersion() {
        return version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
