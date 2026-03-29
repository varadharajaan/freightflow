package com.freightflow.commons.observability.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import io.micrometer.observation.ObservationRegistry;

/**
 * Configures trace context propagation for Apache Kafka producers and consumers.
 *
 * <h3>Problem</h3>
 * <p>By default, distributed traces break at Kafka boundaries. When Service A publishes
 * an event to Kafka and Service B consumes it, the trace context (trace ID, span ID) is
 * lost unless explicitly propagated through Kafka headers.</p>
 *
 * <h3>Solution</h3>
 * <p>Spring Kafka 3.x integrates with Micrometer Observation API to automatically:</p>
 * <ul>
 *   <li><b>Producer side</b>: Injects W3C Trace Context headers ({@code traceparent},
 *       {@code tracestate}) into Kafka {@code ProducerRecord} headers before sending</li>
 *   <li><b>Consumer side</b>: Extracts trace context from Kafka {@code ConsumerRecord}
 *       headers and restores the parent span, so consumer processing appears as a child
 *       span in the same trace</li>
 * </ul>
 *
 * <h3>Trace Flow Through Kafka</h3>
 * <pre>
 * HTTP Request (traceId=abc)
 *   → BookingService.createBooking() [span-1]
 *     → KafkaProducer.send(BookingCreatedEvent) [span-2, headers: traceparent=abc-span2]
 *       → Kafka Topic: freightflow.booking.events
 *         → KafkaConsumer.receive(BookingCreatedEvent) [span-3, parent=span-2, traceId=abc]
 *           → TrackingService.handleBookingCreated() [span-4, traceId=abc]
 * </pre>
 *
 * <h3>Configuration</h3>
 * <p>This configuration is enabled when:</p>
 * <ul>
 *   <li>{@code freightflow.tracing.enabled=true} (default)</li>
 *   <li>Spring Kafka classes are on the classpath</li>
 * </ul>
 *
 * <p>Spring Boot auto-configures the observation-enabled KafkaTemplate and listener
 * container factory when {@code micrometer-tracing} is on the classpath. This configuration
 * explicitly enables observation on the factories to ensure trace propagation is active.</p>
 *
 * @see io.micrometer.observation.ObservationRegistry
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@ConditionalOnProperty(name = "freightflow.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTracingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaTracingConfig.class);

    private final ObservationRegistry observationRegistry;

    /**
     * Constructs the Kafka tracing configuration with the observation registry.
     *
     * @param observationRegistry the Micrometer observation registry for trace propagation
     */
    public KafkaTracingConfig(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
        log.info("Kafka trace propagation configured. "
                + "Trace context will be propagated via Kafka record headers (W3C Trace Context).");
    }

    /**
     * Enables observation (tracing) on the auto-configured {@link KafkaTemplate}.
     *
     * <p>When observation is enabled on the KafkaTemplate, it automatically injects
     * {@code traceparent} and {@code tracestate} W3C Trace Context headers into every
     * Kafka record before sending. This allows consumers to continue the distributed trace.</p>
     *
     * <p>Spring Boot auto-configures a {@link KafkaTemplate} bean. This method sets the
     * observation registry on it, enabling the built-in Micrometer instrumentation.</p>
     *
     * @param kafkaTemplate the Spring Kafka template (auto-configured by Spring Boot)
     * @return the same KafkaTemplate, now observation-enabled
     */
    @Bean
    public KafkaTemplateObservationCustomizer kafkaTemplateObservationCustomizer(
            KafkaTemplate<?, ?> kafkaTemplate) {
        log.debug("Enabling observation on KafkaTemplate for trace header injection");
        kafkaTemplate.setObservationEnabled(true);
        return new KafkaTemplateObservationCustomizer(kafkaTemplate);
    }

    /**
     * Enables observation (tracing) on the auto-configured Kafka listener container factory.
     *
     * <p>When observation is enabled on the listener container, it extracts {@code traceparent}
     * and {@code tracestate} headers from incoming Kafka records, restoring the trace context
     * so that consumer processing spans appear as children of the producer span.</p>
     *
     * @param factory the Kafka listener container factory (auto-configured by Spring Boot)
     * @return a customizer record confirming observation is enabled
     */
    @Bean
    public ConsumerFactoryObservationCustomizer consumerFactoryObservationCustomizer(
            ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        log.debug("Enabling observation on ConcurrentKafkaListenerContainerFactory "
                + "for trace header extraction");
        factory.getContainerProperties().setObservationEnabled(true);
        return new ConsumerFactoryObservationCustomizer(factory);
    }

    /**
     * Marker record confirming that the KafkaTemplate has been configured with observation support.
     *
     * @param kafkaTemplate the observation-enabled Kafka template
     */
    public record KafkaTemplateObservationCustomizer(KafkaTemplate<?, ?> kafkaTemplate) {
    }

    /**
     * Marker record confirming that the listener container factory has been configured
     * with observation support.
     *
     * @param factory the observation-enabled listener container factory
     */
    public record ConsumerFactoryObservationCustomizer(
            ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
    }
}
