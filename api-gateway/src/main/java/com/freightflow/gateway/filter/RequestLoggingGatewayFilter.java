package com.freightflow.gateway.filter;

import com.freightflow.commons.domain.FreightFlowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reactive gateway filter that logs every inbound request and outbound response
 * with structured metadata for observability and debugging.
 *
 * <p>This is the <em>reactive equivalent</em> of a servlet {@code HandlerInterceptor}.
 * Because Spring Cloud Gateway runs on Netty (not Tomcat), traditional servlet filters
 * and interceptors are not available. This {@link GlobalFilter} fulfils the same role
 * in the reactive pipeline.</p>
 *
 * <h3>Logged on Request</h3>
 * <ul>
 *   <li>HTTP method</li>
 *   <li>Request path</li>
 *   <li>Query string (if present)</li>
 *   <li>Client IP address</li>
 *   <li>Correlation ID (injected by {@link CorrelationIdGatewayFilter})</li>
 * </ul>
 *
 * <h3>Logged on Response</h3>
 * <ul>
 *   <li>HTTP status code</li>
 *   <li>Request duration in milliseconds</li>
 *   <li>Correlation ID</li>
 *   <li>{@code WARN} level if response time exceeds {@value #SLOW_REQUEST_THRESHOLD_MS}ms</li>
 * </ul>
 *
 * <p>This filter runs at {@code HIGHEST_PRECEDENCE + 1}, immediately after the
 * {@link CorrelationIdGatewayFilter} so the correlation ID is already available
 * in the request headers.</p>
 *
 * @see CorrelationIdGatewayFilter
 * @see FreightFlowConstants#HEADER_CORRELATION_ID
 */
@Component
public class RequestLoggingGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingGatewayFilter.class);

    /** Threshold in milliseconds above which a request is logged at WARN level as "slow". */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 3_000L;

    /**
     * Logs the inbound request details, delegates to the next filter, then logs the
     * outbound response with elapsed time.
     *
     * @param exchange the current server exchange
     * @param chain    provides a way to delegate to the next filter
     * @return {@link Mono} to indicate when request processing is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(chain, "chain must not be null");

        ServerHttpRequest request = exchange.getRequest();
        long startNanos = System.nanoTime();

        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        String clientIp = resolveClientIp(request);
        String correlationId = Optional.ofNullable(
                request.getHeaders().getFirst(FreightFlowConstants.HEADER_CORRELATION_ID)
        ).orElse("unknown");

        log.info(">>> Gateway request: method={}, path={}, query={}, clientIp={}, correlationId={}",
                method, path, query, clientIp, correlationId);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    int statusCode = (status != null) ? status.value() : 0;

                    if (durationMs > SLOW_REQUEST_THRESHOLD_MS) {
                        log.warn("<<< Gateway response [SLOW]: status={}, durationMs={}, " +
                                        "correlationId={}, path={}",
                                statusCode, durationMs, correlationId, path);
                    } else {
                        log.info("<<< Gateway response: status={}, durationMs={}, correlationId={}",
                                statusCode, durationMs, correlationId);
                    }
                });
    }

    /**
     * Runs immediately after {@link CorrelationIdGatewayFilter}.
     *
     * @return {@code HIGHEST_PRECEDENCE + 1}
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * Resolves the client IP address from the request, preferring the {@code X-Forwarded-For}
     * header (set by load balancers) over the remote address.
     *
     * @param request the inbound HTTP request
     * @return the client IP address, or {@code "unknown"} if it cannot be determined
     */
    private String resolveClientIp(ServerHttpRequest request) {
        // Prefer X-Forwarded-For (set by ALB/Nginx/proxy in front of the gateway)
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For may contain multiple IPs — the first is the original client
            return forwardedFor.split(",")[0].trim();
        }

        return Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress)
                .orElse("unknown");
    }
}
