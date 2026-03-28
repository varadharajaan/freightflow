package com.freightflow.gateway.filter;

import com.freightflow.commons.domain.FreightFlowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Objects;
import java.util.UUID;

/**
 * Reactive gateway filter that ensures every request flowing through the API Gateway
 * carries a correlation ID for end-to-end distributed tracing.
 *
 * <p>This filter runs at {@link Ordered#HIGHEST_PRECEDENCE} so that the correlation ID is
 * available to all subsequent filters (e.g., {@link RequestLoggingGatewayFilter}) and
 * downstream services.</p>
 *
 * <h3>Behavior</h3>
 * <ol>
 *   <li>Checks for an existing {@code X-Correlation-ID} header on the inbound request
 *       (e.g., set by an upstream load balancer or the calling client).</li>
 *   <li>If absent, generates a new {@link UUID} as the correlation ID.</li>
 *   <li>Mutates the request to inject the correlation ID header — this is forwarded to
 *       downstream microservices.</li>
 *   <li>Adds the correlation ID to the outbound response headers — returned to the client.</li>
 *   <li>Stores the correlation ID in the Reactor {@link Context} so it can be accessed
 *       anywhere in the reactive chain without thread-local hacks.</li>
 * </ol>
 *
 * <h3>Reactive vs. Servlet</h3>
 * <p>Unlike the servlet-based {@code CorrelationIdFilter} in {@code commons-observability}
 * (which uses {@code jakarta.servlet.Filter} and MDC thread-locals), this filter operates on
 * the <em>reactive</em> (WebFlux/Netty) stack. It uses the Reactor {@link Context} for
 * propagation instead of MDC, because reactive pipelines do not guarantee a single thread
 * per request. MDC values set in one operator may not be visible in another operator
 * scheduled on a different thread.</p>
 *
 * @see FreightFlowConstants#HEADER_CORRELATION_ID
 * @see RequestLoggingGatewayFilter
 */
@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGatewayFilter.class);

    /** Reactor Context key for the correlation ID. */
    public static final String CONTEXT_CORRELATION_ID = "correlationId";

    /**
     * Filters each exchange to ensure a correlation ID is present on the request,
     * the response, and the Reactor context.
     *
     * @param exchange the current server exchange
     * @param chain    provides a way to delegate to the next filter
     * @return {@link Mono} to indicate when request processing is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(chain, "chain must not be null");

        String correlationId = extractOrGenerateCorrelationId(exchange);

        // Mutate the request to carry the correlation ID downstream
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(FreightFlowConstants.HEADER_CORRELATION_ID, correlationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Add correlation ID to response headers (visible to the calling client)
        ServerHttpResponse response = mutatedExchange.getResponse();
        response.getHeaders().add(FreightFlowConstants.HEADER_CORRELATION_ID, correlationId);

        log.debug("Correlation ID [{}] assigned to request {} {}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath());

        // Continue the filter chain with the correlation ID in Reactor Context
        return chain.filter(mutatedExchange)
                .contextWrite(Context.of(CONTEXT_CORRELATION_ID, correlationId));
    }

    /**
     * Runs at the highest precedence so all subsequent filters have access to the correlation ID.
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Extracts the correlation ID from the inbound request header, or generates a new UUID
     * if the header is missing or blank.
     *
     * @param exchange the current server exchange
     * @return the correlation ID (never {@code null} or blank)
     */
    private String extractOrGenerateCorrelationId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders()
                .getFirst(FreightFlowConstants.HEADER_CORRELATION_ID);

        if (existing != null && !existing.isBlank()) {
            log.debug("Reusing existing correlation ID [{}] from inbound request", existing);
            return existing;
        }

        String generated = UUID.randomUUID().toString();
        log.debug("Generated new correlation ID [{}] — no {} header on inbound request",
                generated, FreightFlowConstants.HEADER_CORRELATION_ID);
        return generated;
    }
}
