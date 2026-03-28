package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for persisting port call data to the {@code port_calls} table.
 *
 * <p>Each port call represents a scheduled stop in a voyage itinerary, recording
 * both estimated and actual arrival/departure times. This entity maps all fields
 * from the domain {@link com.freightflow.vesselschedule.domain.model.PortCall}
 * record to ensure no data is silently dropped during persistence round-trips.</p>
 *
 * <p>This entity lives in the infrastructure layer (outbound adapter) and is
 * NOT exposed to the domain layer.</p>
 *
 * @see com.freightflow.vesselschedule.domain.model.PortCall
 * @see VoyageJpaEntity
 */
@Entity
@Table(name = "port_calls")
public class PortCallJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voyage_id", nullable = false)
    private VoyageJpaEntity voyage;

    @Column(name = "port", nullable = false, length = 10)
    private String port;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "estimated_arrival", nullable = false)
    private Instant estimatedArrival;

    @Column(name = "estimated_departure", nullable = false)
    private Instant estimatedDeparture;

    @Column(name = "actual_arrival")
    private Instant actualArrival;

    @Column(name = "actual_departure")
    private Instant actualDeparture;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * No-arg constructor required by JPA. Also used by the
     * {@link com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.VoyageEntityMapper}
     * to create entities from domain objects.
     */
    public PortCallJpaEntity() {
    }

    // ==================== Getters and Setters ====================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public VoyageJpaEntity getVoyage() { return voyage; }
    public void setVoyage(VoyageJpaEntity voyage) { this.voyage = voyage; }

    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public Instant getEstimatedArrival() { return estimatedArrival; }
    public void setEstimatedArrival(Instant estimatedArrival) { this.estimatedArrival = estimatedArrival; }

    public Instant getEstimatedDeparture() { return estimatedDeparture; }
    public void setEstimatedDeparture(Instant estimatedDeparture) { this.estimatedDeparture = estimatedDeparture; }

    public Instant getActualArrival() { return actualArrival; }
    public void setActualArrival(Instant actualArrival) { this.actualArrival = actualArrival; }

    public Instant getActualDeparture() { return actualDeparture; }
    public void setActualDeparture(Instant actualDeparture) { this.actualDeparture = actualDeparture; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "PortCallJpaEntity[id=%s, port=%s, seq=%d, voyageId=%s]".formatted(
                id, port, sequenceNumber, voyage != null ? voyage.getId() : "null");
    }
}
