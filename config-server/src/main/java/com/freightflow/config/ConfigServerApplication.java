package com.freightflow.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Entry point for the FreightFlow Config Server.
 *
 * <p>This application runs a Spring Cloud Config Server that provides
 * centralized, Git-backed configuration management for all FreightFlow
 * microservices. Services fetch their configuration at startup and can
 * refresh it at runtime using {@code @RefreshScope} and the
 * {@code /actuator/refresh} endpoint.</p>
 *
 * <h3>Key Responsibilities</h3>
 * <ul>
 *   <li>Serves externalized configuration from a Git repository</li>
 *   <li>Supports per-service and per-profile configuration overlays</li>
 *   <li>Enables runtime configuration refresh via {@code @RefreshScope}</li>
 *   <li>Registers with Eureka Discovery Server for service discovery</li>
 * </ul>
 *
 * <h3>Configuration Resolution</h3>
 * <p>The server resolves configuration files in the following order
 * (later sources override earlier ones):</p>
 * <ol>
 *   <li>{@code application.yml} — shared defaults across all services</li>
 *   <li>{@code {application}.yml} — service-specific configuration</li>
 *   <li>{@code {application}-{profile}.yml} — profile-specific overrides</li>
 * </ol>
 *
 * <h3>Access</h3>
 * <ul>
 *   <li>Default port: {@code 8888}</li>
 *   <li>Config endpoint: {@code http://localhost:8888/{application}/{profile}}</li>
 *   <li>Protected via HTTP Basic authentication (see {@code application.yml})</li>
 * </ul>
 *
 * @see ConfigSecurityConfig
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    /**
     * Starts the Config Server with embedded Tomcat on port 8888.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
