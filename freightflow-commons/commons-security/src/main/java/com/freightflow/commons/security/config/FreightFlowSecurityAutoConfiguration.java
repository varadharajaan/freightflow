package com.freightflow.commons.security.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration that provides base OAuth2/OIDC security for all FreightFlow services.
 *
 * <p>This module is the centralized security foundation for the platform. When a service
 * includes {@code commons-security} on its classpath, this auto-configuration registers:</p>
 * <ul>
 *   <li><b>{@link JwtAuthenticationConfig}</b> — Stateless JWT validation via Spring Security's
 *       OAuth2 Resource Server support, with a custom {@link com.freightflow.commons.security.jwt.JwtAuthenticationConverter}
 *       that maps Keycloak JWT claims to Spring Security {@code GrantedAuthority} instances.</li>
 *   <li><b>{@link CorsConfig}</b> — Cross-Origin Resource Sharing configuration with
 *       configurable allowed origins, methods, and headers.</li>
 *   <li><b>{@link SecurityHeadersConfig}</b> — HTTP security response headers (CSP, HSTS,
 *       X-Content-Type-Options, etc.) following OWASP best practices.</li>
 * </ul>
 *
 * <h3>Override Mechanism</h3>
 * <p>Individual services can override any part of this configuration by declaring their own
 * {@code SecurityFilterChain} bean. Spring Security's auto-configuration backs off when a
 * custom bean is present, allowing fine-grained control per service.</p>
 *
 * <h3>Disable Mechanism</h3>
 * <p>Set {@code freightflow.security.enabled=false} in {@code application.yml} to disable
 * JWT validation entirely (useful for local development without Keycloak).</p>
 *
 * <p>Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.</p>
 *
 * @see JwtAuthenticationConfig
 * @see CorsConfig
 * @see SecurityHeadersConfig
 */
@AutoConfiguration
@Import({JwtAuthenticationConfig.class, CorsConfig.class, SecurityHeadersConfig.class})
public class FreightFlowSecurityAutoConfiguration {
}
