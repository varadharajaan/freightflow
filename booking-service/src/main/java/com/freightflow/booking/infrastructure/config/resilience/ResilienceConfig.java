package com.freightflow.booking.infrastructure.config.resilience;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Programmatic Resilience4j configuration for the booking service.
 *
 * <p>While Resilience4j supports YAML-based configuration, programmatic configuration
 * provides type safety, Javadoc, and makes the resilience strategy explicit in code.
 * This is a Principal Engineer decision — resilience is too critical to hide in YAML.</p>
 *
 * <h3>Resilience Patterns Configured</h3>
 * <ul>
 *   <li><b>Circuit Breaker</b>: Prevents cascading failures by stopping calls to a failing service</li>
 *   <li><b>Retry</b>: Automatically retries transient failures with exponential backoff</li>
 *   <li><b>Bulkhead</b>: Limits concurrent calls to prevent resource exhaustion</li>
 *   <li><b>Time Limiter</b>: Enforces timeout on async operations</li>
 * </ul>
 *
 * <h3>Circuit Breaker State Machine</h3>
 * <pre>
 *   CLOSED ──(failure rate >= 50%)──→ OPEN
 *     ↑                                 │
 *     │                          (wait 30 seconds)
 *     │                                 │
 *     └──(success rate >= 60%)──── HALF_OPEN
 * </pre>
 *
 * @see <a href="https://resilience4j.readme.io/">Resilience4j Documentation</a>
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    // ==================== Circuit Breaker ====================

    /**
     * Circuit breaker registry with named instances per external dependency.
     *
     * <p>Configuration:</p>
     * <ul>
     *   <li>Sliding window: 10 calls</li>
     *   <li>Failure rate threshold: 50% (opens circuit after 5 failures in 10 calls)</li>
     *   <li>Wait in open state: 30 seconds before half-open</li>
     *   <li>Permitted calls in half-open: 3 (to probe if service recovered)</li>
     *   <li>Slow call threshold: 2 seconds (calls exceeding this count as failures)</li>
     *   <li>Slow call rate threshold: 80%</li>
     * </ul>
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.info("Configuring Resilience4j Circuit Breaker registry");

        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .slowCallRateThreshold(80)
                .recordExceptions(
                        java.io.IOException.class,
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class
                )
                .ignoreExceptions(
                        com.freightflow.commons.exception.ValidationException.class,
                        com.freightflow.commons.exception.ResourceNotFoundException.class
                )
                .build();

        // More aggressive config for payment/billing (critical path)
        CircuitBreakerConfig criticalConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .failureRateThreshold(30)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .build();

        return CircuitBreakerRegistry.of(defaultConfig,
                io.vavr.collection.HashMap.of(
                        "vesselScheduleService", defaultConfig,
                        "billingService", criticalConfig,
                        "trackingService", defaultConfig,
                        "paymentGateway", criticalConfig
                ).toJavaMap());
    }

    // ==================== Retry ====================

    /**
     * Retry registry with exponential backoff.
     *
     * <p>Configuration:</p>
     * <ul>
     *   <li>Max attempts: 3</li>
     *   <li>Wait duration: 1 second (initial)</li>
     *   <li>Exponential backoff multiplier: 2x (1s → 2s → 4s)</li>
     *   <li>Only retries transient failures (IOException, TimeoutException)</li>
     *   <li>Does NOT retry business exceptions (Validation, NotFound, Conflict)</li>
     * </ul>
     */
    @Bean
    public RetryRegistry retryRegistry() {
        log.info("Configuring Resilience4j Retry registry");

        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .enableExponentialBackoff()
                .exponentialBackoffMultiplier(2.0)
                .retryExceptions(
                        java.io.IOException.class,
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.dao.TransientDataAccessException.class
                )
                .ignoreExceptions(
                        com.freightflow.commons.exception.FreightFlowException.class
                )
                .build();

        return RetryRegistry.of(defaultConfig);
    }

    // ==================== Bulkhead ====================

    /**
     * Bulkhead registry for concurrent call isolation.
     *
     * <p>Limits the number of concurrent calls to a downstream service.
     * Prevents a slow service from consuming all threads in the calling service
     * (Bulkhead Pattern from ship design — isolated compartments).</p>
     *
     * <p>With Virtual Threads, semaphore-based bulkheads are preferred over
     * thread-pool bulkheads (Virtual Threads make thread pools unnecessary).</p>
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        log.info("Configuring Resilience4j Bulkhead registry");

        BulkheadConfig defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(25)
                .maxWaitDuration(Duration.ofMillis(500))
                .build();

        BulkheadConfig criticalConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ofMillis(200))
                .build();

        return BulkheadRegistry.of(defaultConfig,
                io.vavr.collection.HashMap.of(
                        "paymentGateway", criticalConfig
                ).toJavaMap());
    }

    // ==================== Time Limiter ====================

    /**
     * Time limiter for async operation timeouts.
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        log.info("Configuring Resilience4j TimeLimiter registry");

        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterConfig strictConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .cancelRunningFuture(true)
                .build();

        return TimeLimiterRegistry.of(defaultConfig,
                io.vavr.collection.HashMap.of(
                        "paymentGateway", strictConfig
                ).toJavaMap());
    }
}
