package com.freightflow.vesselschedule.infrastructure.adapter.out.cache;

import com.freightflow.vesselschedule.application.port.CacheInvalidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Centralized cache invalidation adapter for command-side mutations.
 */
@Component
public class CentralCacheInvalidationService implements CacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(CentralCacheInvalidationService.class);

    private static final String VOYAGES_CACHE = "voyages";
    private static final String VESSEL_VOYAGES_CACHE = "vessel-voyages";
    private static final String AVAILABLE_ROUTES_CACHE = "available-routes";

    private final CacheManager cacheManager;

    public CentralCacheInvalidationService(CacheManager cacheManager) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager must not be null");
    }

    @Override
    public void invalidateVoyage(UUID voyageId) {
        evict(VOYAGES_CACHE, voyageId);
    }

    @Override
    public void invalidateVoyagesByVessel(UUID vesselId) {
        evict(VESSEL_VOYAGES_CACHE, vesselId);
    }

    @Override
    public void invalidateAvailableRoutes() {
        clear(AVAILABLE_ROUTES_CACHE);
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("Cache evicted: cache={}, key={}", cacheName, key);
        }
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.debug("Cache cleared: cache={}", cacheName);
        }
    }
}
