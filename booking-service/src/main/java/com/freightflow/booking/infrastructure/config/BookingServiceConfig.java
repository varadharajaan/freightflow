package com.freightflow.booking.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Central configuration class for the Booking Service infrastructure layer.
 *
 * <p>Provides Spring bean definitions for infrastructure components that cannot
 * be auto-configured via component scanning. As the service evolves, this class
 * will hold bean definitions for:</p>
 * <ul>
 *   <li>Kafka producer configuration</li>
 *   <li>Object mapper customizations</li>
 *   <li>Resilience4j circuit breaker beans</li>
 *   <li>Custom converters and serializers</li>
 * </ul>
 *
 * <p>Currently, all beans are discovered via component scanning. This class
 * serves as the designated extension point for explicit bean definitions.</p>
 *
 * @see com.freightflow.booking.BookingServiceApplication
 */
@Configuration
public class BookingServiceConfig {

    // Bean definitions will be added here as the service evolves.
    // Examples:
    //   - ObjectMapper customization for domain value objects
    //   - Kafka ProducerFactory and KafkaTemplate
    //   - Resilience4j CircuitBreaker and Retry beans
}
