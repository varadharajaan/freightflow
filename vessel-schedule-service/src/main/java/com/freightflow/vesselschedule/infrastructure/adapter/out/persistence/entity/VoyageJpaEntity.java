package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for persisting voyage aggregates to PostgreSQL.
 *
 * <p>This entity lives in the infrastructure layer (outbound adapter) and is
 * NOT exposed to the domain layer.</p>
 *
 * @see com.freightflow.vesselschedule.domain.model.Voyage
 */
@Entity
@Table(name = "voyages")
@EntityListeners(AuditingEntityListener.class)
public class VoyageJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "vessel_id", nullable = false)
    private UUID vesselId;

    @Column(name = "voyage_number", nullable = false, unique = true, length = 30)
    private String voyageNumber;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_capacity_teu", nullable = false)
    private int totalCapacityTeu;

    @Column(name = "remaining_capacity_teu", nullable = false)
    private int remainingCapacityTeu;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "voyage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sequenceNumber ASC")
    private List<PortCallJpaEntity> portCalls = new ArrayList<>();

    /**
     * No-arg constructor required by JPA. Also used by the
     * {@link com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.VoyageEntityMapper}
     * to create entities from domain objects.
     */
    public VoyageJpaEntity() {
    }

    // ==================== Getters and Setters ====================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getVesselId() { return vesselId; }
    public void setVesselId(UUID vesselId) { this.vesselId = vesselId; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalCapacityTeu() { return totalCapacityTeu; }
    public void setTotalCapacityTeu(int totalCapacityTeu) { this.totalCapacityTeu = totalCapacityTeu; }

    public int getRemainingCapacityTeu() { return remainingCapacityTeu; }
    public void setRemainingCapacityTeu(int remainingCapacityTeu) { this.remainingCapacityTeu = remainingCapacityTeu; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public List<PortCallJpaEntity> getPortCalls() { return portCalls; }
    public void setPortCalls(List<PortCallJpaEntity> portCalls) { this.portCalls = portCalls; }

    @Override
    public String toString() {
        return "VoyageJpaEntity[id=%s, number=%s, vessel=%s, remaining=%d/%dTEU]".formatted(
                id, voyageNumber, vesselId, remainingCapacityTeu, totalCapacityTeu);
    }
}
