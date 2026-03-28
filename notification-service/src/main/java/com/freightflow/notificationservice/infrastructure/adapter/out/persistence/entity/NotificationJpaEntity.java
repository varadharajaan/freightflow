package com.freightflow.notificationservice.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for persisting notification aggregates to PostgreSQL.
 *
 * <p>This entity lives in the infrastructure layer (outbound adapter) and is
 * NOT exposed to the domain layer. The domain works with
 * {@link com.freightflow.notificationservice.domain.model.Notification},
 * and this entity is mapped to/from the domain model via the persistence adapter.</p>
 *
 * <p>Follows the separation mandated by Hexagonal Architecture:
 * domain model is persistence-ignorant, JPA annotations stay in infrastructure.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>JPA auditing via {@link AuditingEntityListener} — auto-populates timestamps</li>
 *   <li>Optimistic locking via {@link Version}</li>
 *   <li>Channel data stored as JSON string for flexibility across channel types</li>
 * </ul>
 *
 * @see com.freightflow.notificationservice.domain.model.Notification
 */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class NotificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    // ==================== Channel Details ====================

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType;

    @Column(name = "channel_data", nullable = false, length = 2000)
    private String channelData;

    // ==================== Content ====================

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    // ==================== Status & Delivery ====================

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error", length = 2000)
    private String error;

    // ==================== Audit Columns ====================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ==================== Optimistic Locking ====================

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Required by JPA. Do not use directly — use the mapper to create entities from domain objects.
     */
    protected NotificationJpaEntity() {
        // JPA requires a no-arg constructor
    }

    // ==================== Getters and Setters ====================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRecipientId() { return recipientId; }
    public void setRecipientId(UUID recipientId) { this.recipientId = recipientId; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getChannelData() { return channelData; }
    public void setChannelData(String channelData) { this.channelData = channelData; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    @Override
    public String toString() {
        return "NotificationJpaEntity[id=%s, channel=%s, status=%s, attempts=%d, recipient=%s]".formatted(
                id, channelType, status, attempts, recipientId);
    }
}
