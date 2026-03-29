package com.freightflow.vesselschedule.application.port;

import java.util.UUID;

/**
 * Application port for write-side cache consistency.
 */
public interface CacheInvalidationService {

    void invalidateVoyage(UUID voyageId);

    void invalidateVoyagesByVessel(UUID vesselId);

    void invalidateAvailableRoutes();
}
