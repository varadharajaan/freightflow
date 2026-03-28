package com.freightflow.vesselschedule.domain.port;

import com.freightflow.vesselschedule.domain.model.Voyage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for voyage persistence.
 *
 * <p>This interface defines the contract that the domain layer expects from
 * the persistence layer (Dependency Inversion Principle).</p>
 *
 * @see com.freightflow.vesselschedule.infrastructure.adapter.out.persistence
 */
public interface VoyageRepository {

    /**
     * Persists a new or updated voyage.
     *
     * @param voyage the voyage aggregate to save
     * @return the saved voyage
     */
    Voyage save(Voyage voyage);

    /**
     * Finds a voyage by its ID.
     *
     * @param voyageId the voyage identifier
     * @return the voyage, or empty if not found
     */
    Optional<Voyage> findById(UUID voyageId);

    /**
     * Finds voyages by vessel.
     *
     * @param vesselId the vessel identifier
     * @return list of voyages for the vessel
     */
    List<Voyage> findByVesselId(UUID vesselId);

    /**
     * Finds scheduled voyages with available capacity.
     *
     * @param minCapacityTeu the minimum remaining TEU capacity required
     * @return list of voyages with sufficient capacity
     */
    List<Voyage> findScheduledWithCapacity(int minCapacityTeu);

    /**
     * Checks whether a voyage exists.
     *
     * @param voyageId the voyage identifier
     * @return true if the voyage exists
     */
    boolean existsById(UUID voyageId);
}
