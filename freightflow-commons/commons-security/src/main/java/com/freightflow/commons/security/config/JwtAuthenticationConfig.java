package com.freightflow.commons.security.config;

import com.freightflow.commons.security.jwt.JwtAuthenticationConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures stateless JWT-based authentication for OAuth2 Resource Server mode.
 *
 * <p>This is the core security filter chain for all FreightFlow microservices. It operates
 * as a pure <b>resource server</b> — it does NOT issue tokens. Token issuance is handled
 * by Keycloak. Each incoming request must carry a valid JWT in the {@code Authorization: Bearer}
 * header.</p>
 *
 * <h3>Authentication Flow</h3>
 * <ol>
 *   <li>Client authenticates with Keycloak and receives a JWT access token</li>
 *   <li>Client sends the JWT in the {@code Authorization: Bearer <token>} header</li>
 *   <li>Spring Security validates the JWT signature via the Keycloak JWKS endpoint</li>
 *   <li>{@link JwtAuthenticationConverter} extracts roles from Keycloak-specific JWT claims</li>
 *   <li>The request proceeds with a populated {@code SecurityContext}</li>
 * </ol>
 *
 * <h3>Stateless Design</h3>
 * <p>Sessions are disabled ({@code STATELESS}) because every request is self-contained —
 * the JWT carries all necessary authentication and authorization information. This enables
 * horizontal scaling without sticky sessions or shared session stores.</p>
 *
 * <h3>CSRF Protection</h3>
 * <p>CSRF is disabled because stateless JWT-based APIs are not vulnerable to CSRF attacks.
 * CSRF exploits browser-stored session cookies, which do not exist in a Bearer token scheme.</p>
 *
 * <h3>Conditional Activation</h3>
 * <p>Enabled by default ({@code freightflow.security.enabled=true}). Set to {@code false}
 * in local development profiles to bypass JWT validation when Keycloak is not running.</p>
 *
 * @see JwtAuthenticationConverter
 * @see CorsConfig
 * @see SecurityHeadersConfig
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnProperty(name = "freightflow.security.enabled", havingValue = "true", matchIfMissing = true)
public class JwtAuthenticationConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationConfig.class);

    /** Paths that are accessible without authentication. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml"
    };

    private final CorsConfig corsConfig;

    /**
     * Creates a new {@code JwtAuthenticationConfig} with the required CORS configuration.
     *
     * @param corsConfig the CORS configuration provider (must not be null)
     */
    public JwtAuthenticationConfig(CorsConfig corsConfig) {
        this.corsConfig = corsConfig;
    }

    /**
     * Builds the primary {@link SecurityFilterChain} for JWT-based resource server security.
     *
     * <p>Configuration summary:</p>
     * <ul>
     *   <li>Public endpoints: actuator health/info, Swagger UI, OpenAPI docs</li>
     *   <li>All other endpoints require a valid JWT</li>
     *   <li>JWT claims are converted via {@link JwtAuthenticationConverter}</li>
     *   <li>Session management: stateless (no HTTP sessions)</li>
     *   <li>CSRF: disabled (stateless API, no cookie-based auth)</li>
     *   <li>Frame options: DENY (prevent clickjacking)</li>
     * </ul>
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring JWT-based security filter chain for FreightFlow resource server");

        http
                // CORS — delegate to CorsConfig bean
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))

                // CSRF — disabled for stateless JWT-based API
                .csrf(csrf -> csrf.disable())

                // Frame options — DENY to prevent clickjacking
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))

                // Session management — stateless, no HTTP sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )

                // OAuth2 Resource Server — JWT validation with custom converter
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        log.info("JWT security filter chain configured — public paths: {}", (Object) PUBLIC_PATHS);

        return http.build();
    }

    /**
     * Creates the Keycloak-aware JWT authentication converter.
     *
     * <p>This converter extracts realm-level and client-level roles from the JWT
     * and maps them to Spring Security {@code GrantedAuthority} objects.</p>
     *
     * @return a new {@link JwtAuthenticationConverter} instance
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        return new JwtAuthenticationConverter();
    }
}
