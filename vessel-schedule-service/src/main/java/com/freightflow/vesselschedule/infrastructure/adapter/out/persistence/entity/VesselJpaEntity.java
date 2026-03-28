package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity;

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
 * JPA entity for persisting vessel aggregates to PostgreSQL.
 *
 * <p>This entity lives in the infrastructure layer (outbound adapter) and is
 * NOT exposed to the domain layer.</p>
 *
 * @see com.freightflow.vesselschedule.domain.model.Vessel
 */
@Entity
@Table(name = "vessels")
@EntityListeners(AuditingEntityListener.class)
public class VesselJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "imo_number", nullable = false, unique = true, length = 20)
    private String imoNumber;

    @Column(name = "flag", nullable = false, length = 50)
    private String flag;

    @Column(name = "capacity_teu", nullable = false)
    private int capacityTeu;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Required by JPA. Do not use directly.
     */
    protected VesselJpaEntity() {
    }

    // ==================== Getters and Setters ====================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImoNumber() { return imoNumber; }
    public void setImoNumber(String imoNumber) { this.imoNumber = imoNumber; }

    public String getFlag() { return flag; }
    public void setFlag(String flag) { this.flag = flag; }

    public int getCapacityTeu() { return capacityTeu; }
    public void setCapacityTeu(int capacityTeu) { this.capacityTeu = capacityTeu; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    @Override
    public String toString() {
        return "VesselJpaEntity[id=%s, name=%s, imo=%s, capacity=%dTEU]".formatted(
                id, name, imoNumber, capacityTeu);
    }
}
