package com.freightflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Config Server.
 *
 * <p>This configuration secures all Config Server endpoints using HTTP Basic
 * authentication. Client microservices must provide valid credentials when
 * fetching their configuration from the server.</p>
 *
 * <h3>Security Rules</h3>
 * <ul>
 *   <li>All endpoints require HTTP Basic authentication</li>
 *   <li>CSRF is disabled because the server operates in a stateless manner
 *       and clients authenticate via credentials embedded in the config
 *       server URL</li>
 * </ul>
 *
 * @see ConfigServerApplication
 */
@Configuration
@EnableWebSecurity
public class ConfigSecurityConfig {

    /**
     * Configures the HTTP security filter chain for the Config Server.
     *
     * <p>Enforces HTTP Basic authentication on all requests and disables
     * CSRF protection for stateless operation.</p>
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
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
