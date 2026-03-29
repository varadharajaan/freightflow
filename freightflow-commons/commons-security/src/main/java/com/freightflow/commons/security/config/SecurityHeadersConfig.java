package com.freightflow.commons.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Configures HTTP security response headers following OWASP best practices.
 *
 * <p>These headers instruct browsers to enforce security policies that mitigate
 * common web vulnerabilities. While FreightFlow is primarily a REST API consumed
 * by service-to-service calls, these headers protect any browser-based interactions
 * (Swagger UI, admin consoles, SPA frontends).</p>
 *
 * <h3>Headers Applied</h3>
 * <table>
 *   <tr><th>Header</th><th>Value</th><th>Purpose</th></tr>
 *   <tr>
 *     <td>{@code Content-Security-Policy}</td>
 *     <td>{@code default-src 'self'}</td>
 *     <td>Restricts resource loading to same-origin only, preventing XSS injection
 *         of external scripts, styles, or images.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code Strict-Transport-Security}</td>
 *     <td>{@code max-age=31536000; includeSubDomains}</td>
 *     <td>Forces browsers to use HTTPS for all future requests to this domain
 *         (HSTS). The 1-year max-age covers certificate rotation cycles.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code X-Content-Type-Options}</td>
 *     <td>{@code nosniff}</td>
 *     <td>Prevents browsers from MIME-type sniffing responses, reducing the risk
 *         of drive-by downloads and content-type confusion attacks.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code X-Frame-Options}</td>
 *     <td>{@code DENY}</td>
 *     <td>Prevents the page from being embedded in iframes, mitigating clickjacking.
 *         Configured in {@link JwtAuthenticationConfig} via {@code headers.frameOptions.deny()}.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code X-XSS-Protection}</td>
 *     <td>{@code 0}</td>
 *     <td>Disables the legacy XSS auditor in older browsers. Modern browsers have
 *         deprecated this feature, and it can actually introduce vulnerabilities.
 *         CSP is the proper replacement.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code Referrer-Policy}</td>
 *     <td>{@code strict-origin-when-cross-origin}</td>
 *     <td>Controls how much referrer information is sent with requests. Sends the
 *         full URL for same-origin requests but only the origin for cross-origin,
 *         preventing URL path leakage to external services.</td>
 *   </tr>
 * </table>
 *
 * @see JwtAuthenticationConfig
 */
@Configuration
public class SecurityHeadersConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeadersConfig.class);

    /** Content Security Policy: restrict all resource loading to same-origin. */
    private static final String CSP_POLICY = "default-src 'self'";

    /** HSTS max-age: 1 year in seconds. */
    private static final String HSTS_HEADER = "max-age=31536000; includeSubDomains";

    /**
     * Provides a customizer that applies security headers to the HttpSecurity configuration.
     *
     * <p>This bean is used by the security filter chain to add all security-related
     * response headers. It is intentionally separated from {@link JwtAuthenticationConfig}
     * to maintain Single Responsibility Principle — authentication configuration is
     * separate from header configuration.</p>
     *
     * @return a configured {@link SecurityHeadersCustomizer} for use in the filter chain
     */
    @Bean
    public SecurityHeadersCustomizer securityHeadersCustomizer() {
        log.info("Security headers configured — CSP='{}', HSTS='{}', X-XSS-Protection=0, " +
                "Referrer-Policy=strict-origin-when-cross-origin", CSP_POLICY, HSTS_HEADER);
        return new SecurityHeadersCustomizer();
    }

    /**
     * Encapsulates security header configuration for reuse across filter chains.
     *
     * <p>Apply to an {@link HttpSecurity} instance via:</p>
     * <pre>{@code
     * http.headers(customizer::configure);
     * }</pre>
     */
    public static class SecurityHeadersCustomizer {

        /**
         * Configures security response headers on the given {@link HttpSecurity} headers spec.
         *
         * @param headers the headers configuration spec
         */
        public void configure(
                org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity> headers) {

            headers
                    // Content-Security-Policy — restrict resource loading to same-origin
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_POLICY))

                    // Strict-Transport-Security — enforce HTTPS with 1-year max-age
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000))

                    // X-Content-Type-Options — prevent MIME sniffing
                    .contentTypeOptions(contentType -> {})

                    // X-XSS-Protection — disabled (legacy, CSP is the replacement)
                    .xssProtection(xss -> xss.disable())

                    // Referrer-Policy — strict-origin-when-cross-origin
                    .referrerPolicy(referrer ->
                            referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
        }
    }
}
