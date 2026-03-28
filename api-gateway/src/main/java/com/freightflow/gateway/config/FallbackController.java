package com.freightflow.gateway.config;

import com.freightflow.commons.domain.FreightFlowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

/**
 * Fallback controller invoked by Resilience4j circuit breakers when a downstream
 * microservice is unavailable or too slow to respond.
 *
 * <p>When a circuit breaker opens (i.e., the downstream service has exceeded its failure
 * threshold), Spring Cloud Gateway forwards the request to the configured fallback URI.
 * Each route in {@link GatewayRouteConfig} specifies {@code forward:/fallback/{service}}
 * as its fallback, which is handled by this controller.</p>
 *
 * <h3>Response Format</h3>
 * <p>Returns an <a href="https://www.rfc-editor.org/rfc/rfc7807">RFC 7807</a> Problem Detail
 * response with:</p>
 * <ul>
 *   <li>HTTP status: {@code 503 Service Unavailable}</li>
 *   <li>Problem type URI from {@link FreightFlowConstants#PROBLEM_EXTERNAL_SERVICE}</li>
 *   <li>Title and detail identifying the unavailable service</li>
 *   <li>Timestamp of the fallback invocation</li>
 * </ul>
 *
 * <p>This pattern provides a graceful degradation experience — clients receive a structured
 * error response instead of a raw connection timeout or 502 Bad Gateway.</p>
 *
 * @see GatewayRouteConfig
 * @see FreightFlowConstants#PROBLEM_EXTERNAL_SERVICE
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    /**
     * Handles circuit breaker fallback requests for any downstream service.
     *
     * <p>The {@code service} path variable identifies which downstream service triggered the
     * circuit breaker (e.g., {@code booking-service}, {@code tracking-service}).</p>
     *
     * @param service the name of the unavailable downstream service
     * @return an RFC 7807 {@link ProblemDetail} with 503 status
     */
    @GetMapping("/fallback/{service}")
    public ProblemDetail fallback(@PathVariable String service) {
        log.warn("Circuit breaker fallback invoked for service [{}] — downstream is unavailable or degraded",
                service);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                String.format(
                        "The %s is currently unavailable. The circuit breaker has been triggered "
                                + "to protect the system. Please retry after a short delay.",
                        service));

        problemDetail.setType(URI.create(FreightFlowConstants.PROBLEM_EXTERNAL_SERVICE));
        problemDetail.setTitle("Service Unavailable");
        problemDetail.setProperty("service", service);
        problemDetail.setProperty("timestamp", Instant.now().toString());

        return problemDetail;
    }
}
