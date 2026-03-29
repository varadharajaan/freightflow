package com.freightflow.vesselschedule.application.port;

import java.util.UUID;

/**
 * Application port for write-side cache consistency.
 */
public interface CacheInvalidationService {

    /**
     * Invalidates the single-voyage read cache entry keyed by the voyage identifier.
     *
     * @param voyageId the voyage identifier whose cached projection should be evicted
     */
    void invalidateVoyage(UUID voyageId);

    /**
     * Invalidates the vessel-voyages read cache entry keyed by the vessel identifier.
     *
     * @param vesselId the vessel identifier whose voyage list cache should be evicted
     */
    void invalidateVoyagesByVessel(UUID vesselId);

    /**
     * Invalidates all cached route-availability query results.
     */
    void invalidateAvailableRoutes();
}
