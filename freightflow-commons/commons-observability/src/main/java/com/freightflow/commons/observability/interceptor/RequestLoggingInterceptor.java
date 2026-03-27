package com.freightflow.commons.observability.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Spring MVC {@link HandlerInterceptor} for structured request/response logging.
 *
 * <p>Unlike the {@link com.freightflow.commons.observability.CorrelationIdFilter} (which is a
 * Servlet Filter operating at the lowest level), this interceptor operates at the Spring MVC
 * handler level and has access to the matched controller method.</p>
 *
 * <h3>What It Logs</h3>
 * <ul>
 *   <li><b>preHandle</b> (DEBUG): HTTP method, URI, query string, content type, client IP</li>
 *   <li><b>postHandle</b> (DEBUG): Handler execution time (before view rendering)</li>
 *   <li><b>afterCompletion</b> (INFO/WARN): Total request time, HTTP status, exception if any</li>
 * </ul>
 *
 * <h3>Spring Advanced Feature: HandlerInterceptor</h3>
 * <p>Interceptors are part of Spring's DispatcherServlet processing chain. They run
 * after the Filter chain but before the actual handler method. Unlike Filters, they
 * have access to the HandlerMethod and can inspect annotations, parameters, etc.</p>
 *
 * <p>Execution order: Filter → Interceptor.preHandle → Controller → Interceptor.postHandle
 * → View → Interceptor.afterCompletion</p>
 *
 * @see com.freightflow.commons.observability.CorrelationIdFilter
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String START_TIME_ATTR = "freightflow.request.startTime";
    private static final long SLOW_REQUEST_THRESHOLD_MS = 2000;

    /**
     * Called before the handler method executes.
     * Logs request details and records the start time for duration calculation.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());

        log.debug("→ REQUEST  {} {} query={} contentType={} remoteAddr={} correlationId={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getContentType(),
                request.getRemoteAddr(),
                MDC.get("correlationId"));

        return true; // Continue processing
    }

    /**
     * Called after the handler method executes but before the response is committed.
     * Useful for adding response headers or modifying the model.
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                            Object handler, ModelAndView modelAndView) {
        // No-op — we log in afterCompletion where we also have exception info
    }

    /**
     * Called after the complete request has finished (including view rendering).
     * Logs the final response status and total execution time.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long durationMs = startTime != null
                ? (System.nanoTime() - startTime) / 1_000_000
                : -1;

        int status = response.getStatus();

        if (ex != null) {
            log.warn("← RESPONSE {} {} status={} duration={}ms exception={}",
                    request.getMethod(), request.getRequestURI(),
                    status, durationMs, ex.getMessage());
        } else if (status >= 500) {
            log.error("← RESPONSE {} {} status={} duration={}ms (server error)",
                    request.getMethod(), request.getRequestURI(), status, durationMs);
        } else if (status >= 400) {
            log.warn("← RESPONSE {} {} status={} duration={}ms (client error)",
                    request.getMethod(), request.getRequestURI(), status, durationMs);
        } else if (durationMs > SLOW_REQUEST_THRESHOLD_MS) {
            log.warn("← RESPONSE {} {} status={} duration={}ms (SLOW — threshold={}ms)",
                    request.getMethod(), request.getRequestURI(),
                    status, durationMs, SLOW_REQUEST_THRESHOLD_MS);
        } else {
            log.info("← RESPONSE {} {} status={} duration={}ms",
                    request.getMethod(), request.getRequestURI(), status, durationMs);
        }
    }
}
