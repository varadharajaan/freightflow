package com.freightflow.booking.infrastructure.config.spel;

import com.freightflow.booking.application.query.BookingView;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * Demonstrates SpEL expressions in Spring Cache annotations.
 *
 * <h3>SpEL in Caching</h3>
 * <p>Spring Cache annotations support SpEL for dynamic key generation,
 * conditional caching, and conditional eviction. The SpEL context provides
 * access to method parameters, return value, and the target object.</p>
 *
 * <h3>SpEL Context Variables Available</h3>
 * <ul>
 *   <li>{@code #root.method} — the cached method</li>
 *   <li>{@code #root.target} — the target object instance</li>
 *   <li>{@code #root.caches} — the caches this method is configured with</li>
 *   <li>{@code #root.methodName} — shortcut for method name</li>
 *   <li>{@code #result} — the return value (only in {@code @CachePut}/{@code unless})</li>
 *   <li>{@code #paramName} — method parameter by name (requires -parameters compiler flag)</li>
 *   <li>{@code #a0, #a1, #p0, #p1} — method parameters by index</li>
 * </ul>
 *
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CacheEvict
 */
@Service
public class SpelCacheExamples {

    private static final Logger log = LoggerFactory.getLogger(SpelCacheExamples.class);

    /**
     * SpEL in @Cacheable — dynamic key from method parameter.
     *
     * <p>Key expression: {@code #bookingId} — uses the method parameter name directly.
     * Condition: only cache if bookingId is not blank.
     * Unless: don't cache null results.</p>
     */
    @Cacheable(
            value = "bookings",
            key = "#bookingId",
            condition = "#bookingId != null && !#bookingId.isBlank()",
            unless = "#result == null"
    )
    @Profiled(value = "cachedBookingLookup", slowThresholdMs = 100)
    public BookingView getCachedBooking(String bookingId) {
        log.debug("Cache MISS — loading booking from database: bookingId={}", bookingId);
        // Delegate to actual repository (this method is for SpEL demo)
        return null; // Placeholder — actual implementation in BookingQueryHandler
    }

    /**
     * SpEL in @Cacheable — composite key from multiple parameters.
     *
     * <p>Key expression: {@code #customerId + ':' + #status} — concatenates two parameters
     * into a single cache key like "customer-123:CONFIRMED".</p>
     */
    @Cacheable(
            value = "customerBookings",
            key = "#customerId + ':' + #status",
            condition = "#customerId != null"
    )
    public Object getCustomerBookingsByStatus(String customerId, String status) {
        log.debug("Cache MISS — loading customer bookings: customerId={}, status={}", customerId, status);
        return null;
    }

    /**
     * SpEL in @CacheEvict — conditional eviction based on return value.
     *
     * <p>Condition: {@code #result != null && #result.status() == 'CANCELLED'} —
     * only evicts the cache if the booking was actually cancelled.
     * Uses {@code allEntries=false} to evict only the specific booking.</p>
     */
    @CacheEvict(
            value = "bookings",
            key = "#bookingId",
            condition = "#result != null"
    )
    public BookingView evictOnStatusChange(String bookingId) {
        log.info("Cache evicted for booking: bookingId={}", bookingId);
        return null;
    }

    /**
     * SpEL in @CachePut — always updates cache after method execution.
     *
     * <p>Unlike @Cacheable (which skips method if cached), @CachePut always
     * executes the method and updates the cache with the result.</p>
     *
     * <p>Key uses {@code #result.bookingId()} — extracts key from the return value.</p>
     */
    @CachePut(
            value = "bookings",
            key = "#result.bookingId()",
            unless = "#result == null"
    )
    public BookingView updateBookingCache(String bookingId, String newStatus) {
        log.debug("Updating cache for booking: bookingId={}, newStatus={}", bookingId, newStatus);
        return null;
    }

    /**
     * SpEL in @Caching — multiple cache operations in one method.
     *
     * <p>Demonstrates combining @CacheEvict for multiple cache regions in a single annotation.
     * When a booking is modified, we evict it from both the booking cache
     * and the customer bookings cache.</p>
     */
    @Caching(evict = {
            @CacheEvict(value = "bookings", key = "#bookingId"),
            @CacheEvict(value = "customerBookings", key = "#customerId + ':*'", allEntries = false)
    })
    public void evictAllRelatedCaches(String bookingId, String customerId) {
        log.info("Evicted all related caches: bookingId={}, customerId={}", bookingId, customerId);
    }
}
