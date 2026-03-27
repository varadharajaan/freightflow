package com.freightflow.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Entry point for the FreightFlow Discovery Server.
 *
 * <p>This application runs a Netflix Eureka Server that acts as the service
 * registry for all FreightFlow microservices. Each service registers itself
 * with this server on startup, enabling dynamic service discovery and
 * client-side load balancing across the platform.</p>
 *
 * <h3>Key Responsibilities</h3>
 * <ul>
 *   <li>Maintains a registry of all active service instances</li>
 *   <li>Provides service location resolution for inter-service communication</li>
 *   <li>Supports heartbeat-based health monitoring of registered services</li>
 *   <li>Exposes a web dashboard for visual inspection of the service registry</li>
 * </ul>
 *
 * <h3>Access</h3>
 * <ul>
 *   <li>Default port: {@code 8761}</li>
 *   <li>Dashboard URL: <a href="http://localhost:8761">http://localhost:8761</a></li>
 *   <li>Dashboard is protected via HTTP Basic authentication (see {@code application.yml})</li>
 * </ul>
 *
 * @see SecurityConfig
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    /**
     * Starts the Eureka Discovery Server with embedded Tomcat on port 8761.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
