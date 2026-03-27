package com.freightflow.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for persisting booking aggregates to PostgreSQL.
 *
 * <p>This entity lives in the infrastructure layer (outbound adapter) and is
 * NOT exposed to the domain layer. The domain works with {@link com.freightflow.booking.domain.model.Booking},
 * and this entity is mapped to/from the domain model via {@link com.freightflow.booking.infrastructure.adapter.out.persistence.mapper.BookingEntityMapper}.</p>
 *
 * <p>Follows the separation mandated by Hexagonal Architecture:
 * domain model is persistence-ignorant, JPA annotations stay in infrastructure.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>JPA auditing via {@link AuditingEntityListener} — auto-populates created/updated timestamps and users</li>
 *   <li>Optimistic locking via {@link Version} — prevents lost updates on concurrent modifications</li>
 *   <li>All columns explicitly mapped — no reliance on implicit naming</li>
 * </ul>
 *
 * @see com.freightflow.booking.domain.model.Booking
 */
@Entity
@Table(name = "bookings")
@EntityListeners(AuditingEntityListener.class)
public class BookingJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // ==================== Cargo Details ====================

    @Column(name = "commodity_code", nullable = false, length = 20)
    private String commodityCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "weight_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal weightValue;

    @Column(name = "weight_unit", nullable = false, length = 5)
    private String weightUnit;

    @Column(name = "container_type", nullable = false, length = 20)
    private String containerType;

    @Column(name = "container_count", nullable = false)
    private int containerCount;

    @Column(name = "origin_port", nullable = false, length = 5)
    private String originPort;

    @Column(name = "destination_port", nullable = false, length = 5)
    private String destinationPort;

    // ==================== Voyage & Cancellation ====================

    @Column(name = "voyage_id")
    private UUID voyageId;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "requested_departure_date", nullable = false)
    private LocalDate requestedDepartureDate;

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
    protected BookingJpaEntity() {
        // JPA requires a no-arg constructor
    }

    // ==================== Getters and Setters ====================
    // Required by JPA — the mapper uses these to populate the entity.
    // No business logic here — that belongs in the domain model.

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getWeightValue() { return weightValue; }
    public void setWeightValue(BigDecimal weightValue) { this.weightValue = weightValue; }

    public String getWeightUnit() { return weightUnit; }
    public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }

    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }

    public int getContainerCount() { return containerCount; }
    public void setContainerCount(int containerCount) { this.containerCount = containerCount; }

    public String getOriginPort() { return originPort; }
    public void setOriginPort(String originPort) { this.originPort = originPort; }

    public String getDestinationPort() { return destinationPort; }
    public void setDestinationPort(String destinationPort) { this.destinationPort = destinationPort; }

    public UUID getVoyageId() { return voyageId; }
    public void setVoyageId(UUID voyageId) { this.voyageId = voyageId; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public LocalDate getRequestedDepartureDate() { return requestedDepartureDate; }
    public void setRequestedDepartureDate(LocalDate requestedDepartureDate) { this.requestedDepartureDate = requestedDepartureDate; }

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
        return "BookingJpaEntity[id=%s, status=%s, customer=%s, route=%s→%s]".formatted(
                id, status, customerId, originPort, destinationPort);
    }
}
