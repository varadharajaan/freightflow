package com.freightflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FreightFlow API Gateway — the single entry point (edge service) for all client traffic.
 *
 * <p>This gateway is built on <b>Spring Cloud Gateway</b> (reactive, Netty-based) and provides:</p>
 * <ul>
 *   <li><b>Dynamic routing</b> — routes requests to downstream microservices discovered via Eureka</li>
 *   <li><b>Load balancing</b> — client-side load balancing through Spring Cloud LoadBalancer</li>
 *   <li><b>Circuit breaking</b> — Resilience4j circuit breakers with service-specific fallbacks</li>
 *   <li><b>Rate limiting</b> — protects downstream services from traffic spikes</li>
 *   <li><b>JWT validation</b> — OAuth2 Resource Server validates access tokens before forwarding</li>
 *   <li><b>Correlation ID injection</b> — generates or propagates {@code X-Correlation-ID} for
 *       distributed tracing across the entire request chain</li>
 *   <li><b>Request/response logging</b> — structured logging of every inbound request and outbound
 *       response with duration metrics</li>
 * </ul>
 *
 * <p><b>Port:</b> {@code 8080} (default — configurable via {@code server.port})</p>
 *
 * <p><b>Important:</b> This application runs on a <em>reactive</em> (WebFlux/Netty) stack, not the
 * traditional servlet (Tomcat) stack. All filters are non-blocking {@link org.springframework.cloud.gateway.filter.GlobalFilter}
 * implementations, not servlet {@code Filter} or {@code HandlerInterceptor} classes.</p>
 *
 * @see com.freightflow.gateway.config.GatewayRouteConfig
 * @see com.freightflow.gateway.filter.CorrelationIdGatewayFilter
 * @see com.freightflow.gateway.filter.RequestLoggingGatewayFilter
 */
@SpringBootApplication
public class ApiGatewayApplication {

    /**
     * Starts the API Gateway on the reactive Netty server.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
