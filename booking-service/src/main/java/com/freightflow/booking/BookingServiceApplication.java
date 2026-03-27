package com.freightflow.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the FreightFlow Booking Service.
 *
 * <p>This microservice owns the Booking aggregate and exposes a REST API
 * for creating, confirming, and cancelling freight bookings. It follows
 * Hexagonal Architecture (Ports &amp; Adapters) with a clean separation
 * between domain, application, and infrastructure layers.</p>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Java 21 virtual threads for high-throughput I/O (enabled via configuration)</li>
 *   <li>Domain-Driven Design with aggregate roots, value objects, and domain events</li>
 *   <li>PostgreSQL persistence with Flyway migrations</li>
 *   <li>Spring Boot Actuator for health checks and metrics</li>
 * </ul>
 *
 * <p>Virtual threads are enabled via {@code spring.threads.virtual.enabled=true}
 * in {@code application.yml}, allowing each request to run on a lightweight
 * virtual thread instead of a platform thread.</p>
 *
 * @see com.freightflow.booking.application.BookingService
 * @see com.freightflow.booking.domain.model.Booking
 */
@SpringBootApplication
public class BookingServiceApplication {

    /**
     * Starts the Booking Service with embedded Tomcat on the configured port.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
