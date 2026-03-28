package com.freightflow.billingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the FreightFlow Billing Service.
 *
 * <p>This microservice owns the Financial Operations bounded context and exposes
 * a REST API for invoice generation, payment processing, and ledger management.
 * It follows Hexagonal Architecture (Ports &amp; Adapters) with a clean separation
 * between domain, application, and infrastructure layers.</p>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Java 21 virtual threads for high-throughput I/O (enabled via configuration)</li>
 *   <li>Domain-Driven Design with aggregate roots, value objects, and domain events</li>
 *   <li>Saga participant for booking-billing distributed transactions</li>
 *   <li>PostgreSQL persistence with Flyway migrations</li>
 *   <li>Kafka consumer for reacting to booking events</li>
 *   <li>Spring Boot Actuator for health checks and metrics</li>
 * </ul>
 *
 * @see com.freightflow.billingservice.application.command.BillingCommandHandler
 * @see com.freightflow.billingservice.domain.model.Invoice
 */
@SpringBootApplication
public class BillingServiceApplication {

    /**
     * Starts the Billing Service with embedded Tomcat on the configured port.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
