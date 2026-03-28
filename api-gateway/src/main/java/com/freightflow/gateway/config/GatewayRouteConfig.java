package com.freightflow.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * Programmatic route definitions for the FreightFlow API Gateway.
 *
 * <p>Routes are defined in Java (not YAML) to leverage compile-time safety, IDE auto-completion,
 * and the full power of the {@link RouteLocatorBuilder} fluent API. This approach makes route
 * configuration refactor-friendly and testable — a deliberate choice over declarative YAML.</p>
 *
 * <p>Each route follows the same pattern:</p>
 * <ol>
 *   <li><b>Path predicate</b> — matches the public API path (e.g., {@code /api/v1/bookings/**})</li>
 *   <li><b>Timestamp header</b> — injects {@code X-Gateway-Timestamp} for downstream latency tracking</li>
 *   <li><b>Circuit breaker</b> — Resilience4j circuit breaker with a fallback URI per service</li>
 *   <li><b>Retry</b> — 3 attempts with 500ms backoff for transient failures</li>
 *   <li><b>Load-balanced URI</b> — {@code lb://service-name} resolves via Eureka discovery</li>
 * </ol>
 *
 * <p>All routes target Eureka-registered service IDs using the {@code lb://} scheme, so the gateway
 * never needs hardcoded host/port values. Eureka + Spring Cloud LoadBalancer handle instance
 * resolution and client-side load balancing.</p>
 *
 * @see org.springframework.cloud.gateway.route.RouteLocator
 * @see org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
 */
@Configuration
public class GatewayRouteConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouteConfig.class);

    /** Header injected by the gateway with the UTC timestamp of request arrival. */
    private static final String HEADER_GATEWAY_TIMESTAMP = "X-Gateway-Timestamp";

    /** Number of retry attempts for transient downstream failures. */
    private static final int RETRY_ATTEMPTS = 3;

    /**
     * Defines all gateway routes for the FreightFlow platform.
     *
     * <p>Currently active routes:</p>
     * <ul>
     *   <li>{@code /api/v1/bookings/**} &rarr; {@code booking-service}</li>
     * </ul>
     *
     * <p>Planned routes (routed but downstream services are not yet deployed):</p>
     * <ul>
     *   <li>{@code /api/v1/tracking/**} &rarr; {@code tracking-service}</li>
     *   <li>{@code /api/v1/billing/**} &rarr; {@code billing-service}</li>
     *   <li>{@code /api/v1/customers/**} &rarr; {@code customer-service}</li>
     *   <li>{@code /api/v1/vessels/**} &rarr; {@code vessel-schedule-service}</li>
     * </ul>
     *
     * @param builder the Spring-provided route locator builder
     * @return a fully configured {@link RouteLocator} with all platform routes
     */
    @Bean
    public RouteLocator freightFlowRoutes(RouteLocatorBuilder builder) {
        log.info("Initializing FreightFlow API Gateway routes");

        RouteLocator routeLocator = builder.routes()

                // ==================== Booking Service ====================
                .route("booking-service", r -> r
                        .path("/api/v1/bookings/**")
                        .filters(f -> f
                                .addRequestHeader(HEADER_GATEWAY_TIMESTAMP, Instant.now().toString())
                                .circuitBreaker(cb -> cb
                                        .setName("bookingServiceCB")
                                        .setFallbackUri("forward:/fallback/booking-service"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(RETRY_ATTEMPTS)
                                        .setBackoff(
                                                java.time.Duration.ofMillis(500),
                                                java.time.Duration.ofMillis(5000),
                                                2,
                                                true)))
                        .uri("lb://booking-service"))

                // ==================== Tracking Service (Planned) ====================
                .route("tracking-service", r -> r
                        .path("/api/v1/tracking/**")
                        .filters(f -> f
                                .addRequestHeader(HEADER_GATEWAY_TIMESTAMP, Instant.now().toString())
                                .circuitBreaker(cb -> cb
                                        .setName("trackingServiceCB")
                                        .setFallbackUri("forward:/fallback/tracking-service"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(RETRY_ATTEMPTS)
                                        .setBackoff(
                                                java.time.Duration.ofMillis(500),
                                                java.time.Duration.ofMillis(5000),
                                                2,
                                                true)))
                        .uri("lb://tracking-service"))

                // ==================== Billing Service (Planned) ====================
                .route("billing-service", r -> r
                        .path("/api/v1/billing/**")
                        .filters(f -> f
                                .addRequestHeader(HEADER_GATEWAY_TIMESTAMP, Instant.now().toString())
                                .circuitBreaker(cb -> cb
                                        .setName("billingServiceCB")
                                        .setFallbackUri("forward:/fallback/billing-service"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(RETRY_ATTEMPTS)
                                        .setBackoff(
                                                java.time.Duration.ofMillis(500),
                                                java.time.Duration.ofMillis(5000),
                                                2,
                                                true)))
                        .uri("lb://billing-service"))

                // ==================== Customer Service (Planned) ====================
                .route("customer-service", r -> r
                        .path("/api/v1/customers/**")
                        .filters(f -> f
                                .addRequestHeader(HEADER_GATEWAY_TIMESTAMP, Instant.now().toString())
                                .circuitBreaker(cb -> cb
                                        .setName("customerServiceCB")
                                        .setFallbackUri("forward:/fallback/customer-service"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(RETRY_ATTEMPTS)
                                        .setBackoff(
                                                java.time.Duration.ofMillis(500),
                                                java.time.Duration.ofMillis(5000),
                                                2,
                                                true)))
                        .uri("lb://customer-service"))

                // ==================== Vessel Schedule Service (Planned) ====================
                .route("vessel-schedule-service", r -> r
                        .path("/api/v1/vessels/**")
                        .filters(f -> f
                                .addRequestHeader(HEADER_GATEWAY_TIMESTAMP, Instant.now().toString())
                                .circuitBreaker(cb -> cb
                                        .setName("vesselScheduleServiceCB")
                                        .setFallbackUri("forward:/fallback/vessel-schedule-service"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(RETRY_ATTEMPTS)
                                        .setBackoff(
                                                java.time.Duration.ofMillis(500),
                                                java.time.Duration.ofMillis(5000),
                                                2,
                                                true)))
                        .uri("lb://vessel-schedule-service"))

                .build();

        log.info("FreightFlow API Gateway routes initialized: booking-service, tracking-service, " +
                "billing-service, customer-service, vessel-schedule-service");

        return routeLocator;
    }
}
