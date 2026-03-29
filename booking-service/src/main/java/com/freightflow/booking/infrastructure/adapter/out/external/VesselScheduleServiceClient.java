package com.freightflow.booking.infrastructure.adapter.out.external;

import com.freightflow.booking.application.port.VesselCapacityPort;
import com.freightflow.commons.observability.profiling.Profiled;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Resilient client for calling the Vessel Schedule Service.
 *
 * <p>Demonstrates ALL Resilience4j patterns working together with proper
 * fallback strategies. This is the <b>outbound adapter</b> for the vessel
 * schedule external dependency.</p>
 *
 * <h3>Resilience Pattern Stack (Applied in Order)</h3>
 * <pre>
 * Request → Bulkhead → TimeLimiter → CircuitBreaker → Retry → Actual Call
 *                                                                    ↓
 *                                              On failure: Retry (3x exponential)
 *                                                                    ↓
 *                                              After retries exhausted: CircuitBreaker opens
 *                                                                    ↓
 *                                              Fallback method returns cached/default value
 * </pre>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Circuit Breaker</b> — stops calling a failing service (prevents cascade)</li>
 *   <li><b>Retry</b> — handles transient failures (network blips, timeouts)</li>
 *   <li><b>Bulkhead</b> — limits concurrent calls (prevents thread exhaustion)</li>
 *   <li><b>Time Limiter</b> — enforces timeout (prevents indefinite waits)</li>
 *   <li><b>Fallback</b> — returns degraded response when service is unavailable</li>
 *   <li><b>Adapter</b> — adapts external service to domain port interface</li>
 * </ul>
 *
 * @see com.freightflow.booking.infrastructure.config.resilience.ResilienceConfig
 */
@Component
public class VesselScheduleServiceClient implements VesselCapacityPort {

    private static final Logger log = LoggerFactory.getLogger(VesselScheduleServiceClient.class);

    private final MeterRegistry meterRegistry;
    private final Counter circuitOpenCounter;
    private final Counter fallbackCounter;

    public VesselScheduleServiceClient(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.circuitOpenCounter = Counter.builder("freightflow.resilience.circuit_open")
                .tag("service", "vesselScheduleService")
                .description("Number of times circuit breaker opened")
                .register(meterRegistry);
        this.fallbackCounter = Counter.builder("freightflow.resilience.fallback")
                .tag("service", "vesselScheduleService")
                .description("Number of fallback invocations")
                .register(meterRegistry);
    }

    /**
     * Reserves vessel capacity for a booking — with full resilience stack.
     *
     * <p>Pattern order: Bulkhead → CircuitBreaker → Retry → Actual HTTP call</p>
     *
     * @param voyageId       the voyage to check
     * @param teu            TEU capacity needed
     * @param idempotencyKey idempotency key for downstream deduplication
     * @return true if capacity is reserved, false otherwise
     */
    @Override
    @CircuitBreaker(name = "vesselScheduleService", fallbackMethod = "reserveCapacityFallback")
    @Retry(name = "vesselScheduleService")
    @Bulkhead(name = "vesselScheduleService")
    @Profiled(value = "reserveVesselCapacity", slowThresholdMs = 1000)
    public boolean reserveCapacity(String voyageId, double teu, String idempotencyKey) {
        log.debug("Reserving vessel capacity: voyageId={}, teu={}, idempotencyKey={}",
                voyageId, teu, idempotencyKey);

        // TODO: Replace with actual HTTP call to vessel-schedule-service
        // using @HttpExchange or RestClient when service is built
        // For now, simulates the call pattern

        log.info("Vessel capacity reserved: voyageId={}, teu={}, idempotencyKey={}",
                voyageId, teu, idempotencyKey);
        return true;
    }

    /**
     * Fetches the vessel schedule for a voyage — with circuit breaker and retry.
     *
     * @param voyageId the voyage ID
     * @return the schedule data, or empty if the service is unavailable
     */
    @CircuitBreaker(name = "vesselScheduleService", fallbackMethod = "getScheduleFallback")
    @Retry(name = "vesselScheduleService")
    @Bulkhead(name = "vesselScheduleService")
    @Profiled(value = "getVesselSchedule", slowThresholdMs = 1500)
    public Optional<VesselScheduleResponse> getVesselSchedule(String voyageId) {
        log.debug("Fetching vessel schedule: voyageId={}", voyageId);

        // TODO: Replace with actual HTTP call via @HttpExchange or RestClient
        // Will be implemented when vessel-schedule-service is built

        log.info("Vessel schedule fetched: voyageId={}", voyageId);
        return Optional.of(new VesselScheduleResponse(voyageId, "MV FreightStar", "In Service"));
    }

    // ==================== Capacity Release (Saga Compensation) ====================

    /**
     * Releases previously reserved vessel capacity — used as a compensating transaction
     * in the Booking Confirmation Saga.
     *
     * <p>When the saga fails after capacity has been reserved, this method is called
     * to release the capacity back to the vessel schedule service. Uses the same
     * resilience stack (circuit breaker + retry) as other vessel service calls.</p>
     *
     * @param voyageId the voyage whose capacity should be released
     * @param teu      the TEU amount to release
     * @param idempotencyKey idempotency key for downstream deduplication
     * @return {@code true} if capacity was successfully released, {@code false} otherwise
     */
    @Override
    @CircuitBreaker(name = "vesselScheduleService", fallbackMethod = "releaseCapacityFallback")
    @Retry(name = "vesselScheduleService")
    @Profiled(value = "releaseVesselCapacity", slowThresholdMs = 1000)
    public boolean releaseCapacity(String voyageId, double teu, String idempotencyKey) {
        log.debug("Releasing vessel capacity: voyageId={}, teu={}, idempotencyKey={}",
                voyageId, teu, idempotencyKey);

        // TODO: Replace with actual HTTP call to vessel-schedule-service
        // using @HttpExchange or RestClient when service is built.
        // For now, simulates a successful capacity release.

        log.info("Vessel capacity released: voyageId={}, teu={}, idempotencyKey={}",
                voyageId, teu, idempotencyKey);
        return true;
    }

    /**
     * Fallback when vessel capacity release fails.
     *
     * <p>Strategy: Return false and log an error. Unlike capacity checks (which fail-safe
     * by denying), capacity release failures require manual intervention to reconcile
     * the reserved capacity. The saga orchestrator logs this as a compensation failure.</p>
     *
     * @param voyageId  the voyage ID
     * @param teu       the TEU amount
     * @param throwable the cause of the failure
     * @return {@code false} (capacity was NOT released)
     */
    private boolean releaseCapacityFallback(String voyageId, double teu, String idempotencyKey, Throwable throwable) {
        logFallback("releaseCapacity", voyageId, throwable);
        fallbackCounter.increment();

        if (throwable instanceof CallNotPermittedException) {
            circuitOpenCounter.increment();
            log.error("Circuit breaker OPEN for vesselScheduleService — capacity release failed. " +
                      "Manual reconciliation required for voyageId={}, teu={}, idempotencyKey={}",
                    voyageId, teu, idempotencyKey);
        }

        // Capacity was NOT released — manual intervention needed
        return false;
    }

    // ==================== Fallback Methods ====================

    /**
     * Fallback when vessel capacity reservation fails.
     *
     * <p>Strategy: Return false (deny booking) when we can't verify capacity.
     * This is a <b>fail-safe</b> approach — we don't overbook.</p>
     *
     * @param voyageId    the voyage ID
     * @param teu the required TEU
     * @param idempotencyKey idempotency key
     * @param throwable   the cause of the failure
     * @return false (deny the booking as a safety measure)
     */
    private boolean reserveCapacityFallback(String voyageId, double teu, String idempotencyKey, Throwable throwable) {
        logFallback("reserveCapacity", voyageId, throwable);
        fallbackCounter.increment();

        if (throwable instanceof CallNotPermittedException) {
            circuitOpenCounter.increment();
            log.error("Circuit breaker OPEN for vesselScheduleService — denying capacity reservation: " +
                    "voyageId={}, teu={}, idempotencyKey={}", voyageId, teu, idempotencyKey);
        }

        // Fail-safe: deny booking when capacity cannot be reserved
        return false;
    }

    /**
     * Fallback when vessel schedule fetch fails.
     *
     * <p>Strategy: Return empty (graceful degradation). The booking can still proceed
     * without schedule details — they'll be populated later when the service recovers.</p>
     *
     * @param voyageId  the voyage ID
     * @param throwable the cause of the failure
     * @return empty optional (graceful degradation)
     */
    private Optional<VesselScheduleResponse> getScheduleFallback(String voyageId, Throwable throwable) {
        logFallback("getVesselSchedule", voyageId, throwable);
        fallbackCounter.increment();

        // Graceful degradation: return empty, booking continues without schedule
        return Optional.empty();
    }

    private void logFallback(String operation, String voyageId, Throwable throwable) {
        log.warn("Fallback invoked for {}(): voyageId={}, cause={}, message={}",
                operation, voyageId,
                throwable.getClass().getSimpleName(),
                throwable.getMessage());
    }

    // ==================== Response Records ====================

    /**
     * Simplified vessel schedule response (from vessel-schedule-service).
     */
    public record VesselScheduleResponse(
            String voyageId,
            String vesselName,
            String status
    ) {}
}
