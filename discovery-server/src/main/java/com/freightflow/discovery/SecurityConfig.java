package com.freightflow.discovery;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Eureka Discovery Server.
 *
 * <p>This configuration secures the Eureka dashboard and API endpoints using
 * HTTP Basic authentication while permitting unauthenticated access to
 * Eureka's static resources (CSS, JS, images) required to render the
 * dashboard UI correctly.</p>
 *
 * <h3>Security Rules</h3>
 * <ul>
 *   <li>{@code /eureka/**} — permitted without authentication (static assets)</li>
 *   <li>All other endpoints — require HTTP Basic authentication</li>
 *   <li>CSRF is disabled because the server operates in a stateless manner
 *       and all clients authenticate via credentials in the Eureka service URL</li>
 * </ul>
 *
 * @see DiscoveryServerApplication
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the HTTP security filter chain for the Eureka server.
     *
     * <p>Permits unauthenticated access to Eureka static resources at
     * {@code /eureka/**} and enforces HTTP Basic authentication on all
     * other requests. CSRF protection is disabled for stateless operation.</p>
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if a security configuration error occurs
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/eureka/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
