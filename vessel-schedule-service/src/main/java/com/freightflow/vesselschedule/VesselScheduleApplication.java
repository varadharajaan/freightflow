package com.freightflow.vesselschedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the FreightFlow Vessel Schedule Service.
 *
 * <p>This microservice owns the Fleet and Voyage Management bounded context and
 * exposes a REST API for vessel fleet management, voyage schedules, route optimization,
 * and capacity management. It follows Hexagonal Architecture (Ports &amp; Adapters)
 * with a clean separation between domain, application, and infrastructure layers.</p>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Java 21 virtual threads for high-throughput I/O (enabled via configuration)</li>
 *   <li>Domain-Driven Design with aggregate roots, value objects, and domain events</li>
 *   <li>Caffeine caching for high-throughput schedule lookups</li>
 *   <li>PostgreSQL persistence with Flyway migrations</li>
 *   <li>Kafka consumer for reacting to booking events (capacity reservation)</li>
 *   <li>Spring Boot Actuator for health checks and metrics</li>
 * </ul>
 *
 * @see com.freightflow.vesselschedule.application.command.VesselCommandHandler
 * @see com.freightflow.vesselschedule.domain.model.Vessel
 * @see com.freightflow.vesselschedule.domain.model.Voyage
 */
@SpringBootApplication
@EnableCaching
public class VesselScheduleApplication {

    /**
     * Starts the Vessel Schedule Service with embedded Tomcat on the configured port.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(VesselScheduleApplication.class, args);
    }
}
