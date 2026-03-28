package com.freightflow.vesselschedule.domain.port;

import com.freightflow.vesselschedule.domain.model.Vessel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for vessel persistence.
 *
 * <p>This interface defines the contract that the domain layer expects from
 * the persistence layer (Dependency Inversion Principle).</p>
 *
 * @see com.freightflow.vesselschedule.infrastructure.adapter.out.persistence
 */
public interface VesselRepository {

    /**
     * Persists a new or updated vessel.
     *
     * @param vessel the vessel aggregate to save
     * @return the saved vessel
     */
    Vessel save(Vessel vessel);

    /**
     * Finds a vessel by its ID.
     *
     * @param vesselId the vessel identifier
     * @return the vessel, or empty if not found
     */
    Optional<Vessel> findById(UUID vesselId);

    /**
     * Finds a vessel by its IMO number.
     *
     * @param imoNumber the IMO identification number
     * @return the vessel, or empty if not found
     */
    Optional<Vessel> findByImoNumber(String imoNumber);

    /**
     * Finds all active vessels.
     *
     * @return list of active vessels
     */
    List<Vessel> findAllActive();
}
