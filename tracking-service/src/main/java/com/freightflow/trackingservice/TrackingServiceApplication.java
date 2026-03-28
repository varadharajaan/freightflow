package com.freightflow.trackingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the FreightFlow Tracking Service.
 *
 * <p>This microservice owns the Container Tracking bounded context and exposes
 * a REST API for real-time container position tracking, milestone events, and
 * geofencing alerts. It follows Hexagonal Architecture (Ports &amp; Adapters)
 * with a clean separation between domain, application, and infrastructure layers.</p>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Java 21 virtual threads for high-throughput I/O (enabled via configuration)</li>
 *   <li>Domain-Driven Design with aggregate roots, value objects, and domain events</li>
 *   <li>PostgreSQL persistence with Flyway migrations</li>
 *   <li>Kafka consumer for reacting to booking events</li>
 *   <li>WebSocket support for live container tracking updates</li>
 *   <li>Spring Boot Actuator for health checks and metrics</li>
 * </ul>
 *
 * <p>Virtual threads are enabled via {@code spring.threads.virtual.enabled=true}
 * in {@code application.yml}, allowing each request to run on a lightweight
 * virtual thread instead of a platform thread.</p>
 *
 * @see com.freightflow.trackingservice.application.command.TrackingCommandHandler
 * @see com.freightflow.trackingservice.domain.model.Container
 */
@SpringBootApplication
public class TrackingServiceApplication {

    /**
     * Starts the Tracking Service with embedded Tomcat on the configured port.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TrackingServiceApplication.class, args);
    }
}
