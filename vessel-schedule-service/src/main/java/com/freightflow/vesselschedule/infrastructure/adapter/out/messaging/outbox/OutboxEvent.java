package com.freightflow.vesselschedule.infrastructure.adapter.out.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox event persisted in the same database transaction as aggregate changes.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(UUID eventId,
                                      String aggregateType,
                                      UUID aggregateId,
                                      String eventType,
                                      String payload,
                                      String metadata) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.id = UUID.randomUUID();
        outboxEvent.eventId = eventId;
        outboxEvent.aggregateType = aggregateType;
        outboxEvent.aggregateId = aggregateId;
        outboxEvent.eventType = eventType;
        outboxEvent.payload = payload;
        outboxEvent.metadata = metadata;
        outboxEvent.status = OutboxEventStatus.PENDING;
        outboxEvent.failureCount = 0;
        outboxEvent.createdAt = Instant.now();
        return outboxEvent;
    }

    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String errorMessage) {
        this.status = OutboxEventStatus.FAILED;
        this.failureCount++;
        this.lastError = errorMessage;
    }

    public void resetToPending() {
        this.status = OutboxEventStatus.PENDING;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getMetadata() { return metadata; }
    public OutboxEventStatus getStatus() { return status; }
    public int getFailureCount() { return failureCount; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
