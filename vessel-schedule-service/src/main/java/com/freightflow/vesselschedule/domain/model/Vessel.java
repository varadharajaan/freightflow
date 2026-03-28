package com.freightflow.vesselschedule.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Vessel Aggregate Root — represents a container vessel in the FreightFlow fleet.
 *
 * <p>This class encapsulates all business rules for vessel management including
 * vessel identity, capacity, operational status, and fleet metadata.</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Aggregate Root</b> (DDD) — single entry point for vessel mutations</li>
 *   <li><b>Factory Method</b> — {@link #create} encapsulates creation logic</li>
 * </ul>
 *
 * @see Voyage
 */
public class Vessel {

    private final UUID vesselId;
    private final String name;
    private final String imoNumber;
    private final String flag;
    private final int capacityTeu;

    private VesselStatus status;
    private long version;

    /**
     * Private constructor — use {@link #create} factory method.
     */
    private Vessel(UUID vesselId, String name, String imoNumber,
                   String flag, int capacityTeu) {
        this.vesselId = Objects.requireNonNull(vesselId, "Vessel ID must not be null");
        this.name = Objects.requireNonNull(name, "Vessel name must not be null");
        this.imoNumber = Objects.requireNonNull(imoNumber, "IMO number must not be null");
        this.flag = Objects.requireNonNull(flag, "Flag must not be null");
        this.capacityTeu = capacityTeu;
        this.status = VesselStatus.ACTIVE;
        this.version = 0;
    }

    // ==================== Factory Method ====================

    /**
     * Factory method to register a new vessel in the fleet.
     *
     * @param name        the vessel name
     * @param imoNumber   the IMO identification number
     * @param flag        the vessel flag state
     * @param capacityTeu the maximum TEU capacity
     * @return a new Vessel in ACTIVE status
     * @throws IllegalArgumentException if capacityTeu is not positive
     */
    public static Vessel create(String name, String imoNumber, String flag, int capacityTeu) {
        if (capacityTeu <= 0) {
            throw new IllegalArgumentException("Capacity TEU must be positive, got: " + capacityTeu);
        }
        return new Vessel(UUID.randomUUID(), name, imoNumber, flag, capacityTeu);
    }

    /**
     * Reconstitutes a Vessel from persisted state.
     *
     * @return a Vessel in its persisted state
     */
    public static Vessel reconstitute(UUID vesselId, String name, String imoNumber,
                                       String flag, int capacityTeu,
                                       VesselStatus status, long version) {
        var vessel = new Vessel(vesselId, name, imoNumber, flag, capacityTeu);
        vessel.status = status;
        vessel.version = version;
        return vessel;
    }

    // ==================== Accessors ====================

    public UUID getVesselId() { return vesselId; }
    public String getName() { return name; }
    public String getImoNumber() { return imoNumber; }
    public String getFlag() { return flag; }
    public int getCapacityTeu() { return capacityTeu; }
    public VesselStatus getStatus() { return status; }
    public long getVersion() { return version; }

    @Override
    public String toString() {
        return "Vessel[id=%s, name=%s, imo=%s, capacity=%dTEU, status=%s]".formatted(
                vesselId, name, imoNumber, capacityTeu, status);
    }

    /**
     * Vessel operational status.
     */
    public enum VesselStatus {
        /** Vessel is active and available for voyages. */
        ACTIVE,
        /** Vessel is in maintenance/dry dock. */
        MAINTENANCE,
        /** Vessel is retired from the fleet. */
        RETIRED
    }
}
