package com.freightflow.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the FreightFlow Notification Service.
 *
 * <p>This microservice owns the Notification aggregate and provides multi-channel
 * notification delivery (email, SMS, webhook). It is a <b>pure consumer</b> — it
 * listens to events from all other FreightFlow services and sends notifications
 * based on templates and channel configuration.</p>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Java 21 virtual threads for high-throughput I/O (enabled via configuration)</li>
 *   <li>Multi-channel delivery via Strategy pattern (Email, SMS, Webhook)</li>
 *   <li>Thymeleaf template engine for email body rendering</li>
 *   <li>Kafka consumer for booking, billing, and tracking events</li>
 *   <li>PostgreSQL persistence with Flyway migrations</li>
 *   <li>Retry logic with configurable attempts and backoff</li>
 *   <li>Spring Boot Actuator for health checks and metrics</li>
 * </ul>
 *
 * <p>Virtual threads are enabled via {@code spring.threads.virtual.enabled=true}
 * in {@code application.yml}, allowing each notification send to run on a lightweight
 * virtual thread instead of a platform thread.</p>
 *
 * @see com.freightflow.notificationservice.application.command.NotificationCommandHandler
 * @see com.freightflow.notificationservice.domain.model.Notification
 */
@SpringBootApplication
public class NotificationServiceApplication {

    /**
     * Starts the Notification Service with embedded Tomcat on the configured port.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
