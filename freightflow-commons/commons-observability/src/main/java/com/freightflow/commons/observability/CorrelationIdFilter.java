package com.freightflow.commons.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates MDC (Mapped Diagnostic Context) for every HTTP request.
 *
 * <p>This filter ensures that every log statement within a request includes:</p>
 * <ul>
 *   <li><b>correlationId</b> — a unique ID for tracing the request across services.
 *       If the upstream caller provides an {@code X-Correlation-ID} header, it is reused;
 *       otherwise, a new UUID is generated.</li>
 *   <li><b>requestMethod</b> — HTTP method (GET, POST, etc.)</li>
 *   <li><b>requestUri</b> — the request URI path</li>
 * </ul>
 *
 * <p>The correlation ID is also set as a response header so downstream services
 * and clients can use it for debugging.</p>
 *
 * <p>Registered with highest precedence ({@link Ordered#HIGHEST_PRECEDENCE}) to ensure
 * MDC is populated before any other filter or interceptor logs anything.</p>
 *
 * @see org.slf4j.MDC
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String REQUEST_METHOD_MDC_KEY = "requestMethod";
    public static final String REQUEST_URI_MDC_KEY = "requestUri";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String correlationId = extractOrGenerateCorrelationId(request);

        try {
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            MDC.put(REQUEST_METHOD_MDC_KEY, request.getMethod());
            MDC.put(REQUEST_URI_MDC_KEY, request.getRequestURI());

            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            log.debug("Request started: method={}, uri={}, correlationId={}",
                    request.getMethod(), request.getRequestURI(), correlationId);

            filterChain.doFilter(request, response);

            log.debug("Request completed: method={}, uri={}, status={}, correlationId={}",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), correlationId);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Extracts the correlation ID from the request header, or generates a new one.
     *
     * <p>This supports distributed tracing — if the API Gateway or upstream service
     * already set a correlation ID, we reuse it to maintain the trace chain.</p>
     *
     * @param request the HTTP request
     * @return the correlation ID
     */
    private String extractOrGenerateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.trace("Generated new correlationId={}", correlationId);
        } else {
            log.trace("Reusing correlationId={} from upstream", correlationId);
        }
        return correlationId;
    }
}
