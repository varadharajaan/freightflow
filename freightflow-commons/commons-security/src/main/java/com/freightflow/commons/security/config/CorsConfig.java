package com.freightflow.commons.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Cross-Origin Resource Sharing (CORS) configuration for FreightFlow services.
 *
 * <p>CORS is a browser security mechanism that restricts which origins can make requests
 * to the API. This configuration defines the allowed origins, methods, and headers for
 * cross-origin requests from SPA frontends and external API consumers.</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code freightflow.security.cors.allowed-origins} — Comma-separated list of allowed
 *       origins. Defaults to {@code *} (all origins) for development. In production, this
 *       should be restricted to specific frontend domains.</li>
 * </ul>
 *
 * <h3>Allowed Headers</h3>
 * <p>The configuration permits standard authentication and content headers, plus FreightFlow
 * custom headers ({@code X-Correlation-ID}, {@code Idempotency-Key}) that are used for
 * distributed tracing and idempotent request handling.</p>
 *
 * <h3>Exposed Headers</h3>
 * <p>Rate-limiting headers and the correlation ID are exposed to JavaScript clients so
 * that SPA frontends can implement retry logic and propagate trace context.</p>
 *
 * @see JwtAuthenticationConfig
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /** Default max age for CORS preflight cache (1 hour). */
    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600L;

    private final String allowedOrigins;

    /**
     * Creates a new {@code CorsConfig} with the specified allowed origins.
     *
     * @param allowedOrigins comma-separated list of allowed origins (defaults to {@code *})
     */
    public CorsConfig(
            @Value("${freightflow.security.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Creates the CORS configuration source applied to all endpoints.
     *
     * <p>This bean is referenced by {@link JwtAuthenticationConfig} to wire CORS
     * into the security filter chain, ensuring CORS is processed before authentication.</p>
     *
     * @return the configured CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins — configurable via property
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);

        // Allowed HTTP methods
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Allowed request headers — standard + FreightFlow custom headers
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Correlation-ID",
                "Idempotency-Key",
                "X-Tenant-ID"
        ));

        // Exposed response headers — accessible to JavaScript clients
        configuration.setExposedHeaders(List.of(
                "X-Correlation-ID",
                "X-RateLimit-Remaining",
                "X-RateLimit-Limit",
                "X-RateLimit-Reset"
        ));

        // Allow credentials (cookies, Authorization headers)
        configuration.setAllowCredentials(!origins.contains("*"));

        // Preflight cache duration — browsers cache OPTIONS responses for this duration
        configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configured — allowedOrigins={}, maxAge={}s, credentials={}",
                origins, PREFLIGHT_MAX_AGE_SECONDS, configuration.getAllowCredentials());

        return source;
    }
}
