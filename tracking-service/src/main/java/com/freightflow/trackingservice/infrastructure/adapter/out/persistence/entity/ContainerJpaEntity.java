package com.freightflow.trackingservice.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for persisting container tracking aggregates to PostgreSQL.
 *
 * <p>This entity lives in the infrastructure layer (outbound adapter) and is
 * NOT exposed to the domain layer. The domain works with
 * {@link com.freightflow.trackingservice.domain.model.Container},
 * and this entity is mapped to/from the domain model via the persistence adapter.</p>
 *
 * <p>Follows the separation mandated by Hexagonal Architecture:
 * domain model is persistence-ignorant, JPA annotations stay in infrastructure.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>JPA auditing via {@link AuditingEntityListener} — auto-populates timestamps</li>
 *   <li>Optimistic locking via {@link Version} — prevents lost updates</li>
 *   <li>All columns explicitly mapped — no reliance on implicit naming</li>
 * </ul>
 *
 * @see com.freightflow.trackingservice.domain.model.Container
 */
@Entity
@Table(name = "containers")
@EntityListeners(AuditingEntityListener.class)
public class ContainerJpaEntity {

    @Id
    @Column(name = "container_id", nullable = false, updatable = false, length = 20)
    private String containerId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // ==================== Position ====================

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "position_timestamp")
    private Instant positionTimestamp;

    @Column(name = "position_source", length = 10)
    private String positionSource;

    // ==================== Voyage ====================

    @Column(name = "voyage_id")
    private UUID voyageId;

    // ==================== Audit Columns ====================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ==================== Optimistic Locking ====================

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Required by JPA. Do not use directly — use the persistence adapter.
     */
    protected ContainerJpaEntity() {
        // JPA requires a no-arg constructor
    }

    // ==================== Getters and Setters ====================

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Instant getPositionTimestamp() { return positionTimestamp; }
    public void setPositionTimestamp(Instant positionTimestamp) { this.positionTimestamp = positionTimestamp; }

    public String getPositionSource() { return positionSource; }
    public void setPositionSource(String positionSource) { this.positionSource = positionSource; }

    public UUID getVoyageId() { return voyageId; }
    public void setVoyageId(UUID voyageId) { this.voyageId = voyageId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    @Override
    public String toString() {
        return "ContainerJpaEntity[id=%s, status=%s, bookingId=%s]".formatted(
                containerId, status, bookingId);
    }
}
