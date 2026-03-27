package com.freightflow.commons.observability.resilience;

import com.freightflow.commons.domain.FreightFlowConstants;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter using Bucket4j (Token Bucket algorithm).
 *
 * <p>Applies per-client rate limiting based on the client's IP address.
 * In production, this would use Redis-backed buckets for distributed rate limiting
 * across multiple pods. The current implementation uses in-memory buckets.</p>
 *
 * <h3>Rate Limit Headers (RFC 6585)</h3>
 * <p>Every response includes rate limit headers:</p>
 * <pre>
 * X-RateLimit-Limit: 100       (max requests per window)
 * X-RateLimit-Remaining: 87    (remaining requests in current window)
 * X-RateLimit-Reset: 1711546232 (epoch seconds when window resets)
 * </pre>
 *
 * <h3>Token Bucket Algorithm</h3>
 * <pre>
 * Bucket starts with 100 tokens. Each request consumes 1 token.
 * Tokens refill at 100 per minute (greedy refill).
 * When bucket is empty → 429 Too Many Requests.
 * </pre>
 *
 * @see <a href="https://github.com/bucket4j/bucket4j">Bucket4j GitHub</a>
 */
@Component
@ConditionalOnProperty(name = "freightflow.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    /** Max requests per client per minute. */
    private static final int RATE_LIMIT = 100;
    /** Refill period. */
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    /** In-memory bucket cache per client. TODO: Replace with Redis in production. */
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String clientId = resolveClientId(request);
        Bucket bucket = bucketCache.computeIfAbsent(clientId, this::createBucket);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        // Always set rate limit headers
        response.setHeader(FreightFlowConstants.HEADER_RATE_LIMIT_LIMIT, String.valueOf(RATE_LIMIT));
        response.setHeader(FreightFlowConstants.HEADER_RATE_LIMIT_REMAINING,
                String.valueOf(probe.getRemainingTokens()));
        response.setHeader(FreightFlowConstants.HEADER_RATE_LIMIT_RESET,
                String.valueOf(Instant.now().plusNanos(probe.getNanosToWaitForRefill()).getEpochSecond()));

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;

            log.warn("Rate limit exceeded: clientId={}, retryAfter={}s", clientId, retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            response.getWriter().write("""
                    {
                        "type": "%s",
                        "title": "Rate Limit Exceeded",
                        "status": 429,
                        "detail": "You have exceeded the rate limit of %d requests per minute",
                        "retryAfter": %d,
                        "timestamp": "%s"
                    }
                    """.formatted(
                    FreightFlowConstants.PROBLEM_RATE_LIMIT,
                    RATE_LIMIT,
                    retryAfterSeconds,
                    Instant.now().toString()));
        }
    }

    /**
     * Only apply rate limiting to API endpoints, not actuator or static resources.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    /**
     * Creates a new token bucket for a client.
     * Uses greedy refill — tokens are added gradually, not all at once.
     */
    private Bucket createBucket(String clientId) {
        log.debug("Creating rate limit bucket for client: {}", clientId);
        return Bucket.builder()
                .addLimit(Bandwidth.classic(RATE_LIMIT, Refill.greedy(RATE_LIMIT, REFILL_PERIOD)))
                .build();
    }

    /**
     * Resolves the client identifier for rate limiting.
     * Uses X-Forwarded-For header (from load balancer) or falls back to remote address.
     */
    private String resolveClientId(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
