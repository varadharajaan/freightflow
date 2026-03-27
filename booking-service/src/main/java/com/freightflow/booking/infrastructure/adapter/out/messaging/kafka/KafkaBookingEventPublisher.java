package com.freightflow.booking.infrastructure.adapter.out.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.booking.domain.port.BookingEventPublisher;
import com.freightflow.booking.infrastructure.config.kafka.KafkaTopicConfig;
import com.freightflow.commons.observability.profiling.Profiled;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka outbound adapter for publishing booking domain events.
 *
 * <p>Implements the {@link BookingEventPublisher} domain port, replacing the
 * in-process {@code SpringEventBookingPublisher} with real Kafka messaging
 * for cross-service event-driven communication.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts domain port to Kafka infrastructure</li>
 *   <li><b>Key = bookingId</b> — ensures per-aggregate ordering within a partition</li>
 *   <li><b>Correlation ID in headers</b> — propagates MDC correlationId for distributed tracing</li>
 *   <li><b>JSON serialization</b> — via Jackson ObjectMapper (Avro migration planned)</li>
 *   <li><b>Async send with callback</b> — non-blocking, logs success/failure</li>
 *   <li><b>Metrics</b> — counts published events per type for Prometheus/Grafana</li>
 * </ul>
 *
 * @see BookingEventPublisher
 * @see KafkaTopicConfig
 */
@Component
public class KafkaBookingEventPublisher implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaBookingEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter eventsPublishedCounter;

    /**
     * Constructor injection — depends on KafkaTemplate, ObjectMapper, and MeterRegistry.
     * All fields are final (Dependency Inversion Principle).
     */
    public KafkaBookingEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                       ObjectMapper objectMapper,
                                       MeterRegistry meterRegistry) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "KafkaTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.eventsPublishedCounter = Counter.builder("freightflow.events.published")
                .description("Total domain events published to Kafka")
                .register(meterRegistry);
    }

    /**
     * Publishes a single domain event to the booking.events Kafka topic.
     *
     * <p>The event is serialized to JSON with the bookingId as the message key
     * (ensures ordering within a partition). The MDC correlation ID is propagated
     * as a Kafka header for distributed tracing.</p>
     *
     * @param event the domain event to publish
     */
    @Override
    @Profiled(value = "kafkaPublishEvent", slowThresholdMs = 200)
    public void publish(BookingEvent event) {
        log.debug("Publishing event to Kafka: type={}, bookingId={}, eventId={}",
                event.eventType(), event.bookingId().asString(), event.eventId());

        try {
            String payload = objectMapper.writeValueAsString(event);
            String key = event.bookingId().asString();

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaTopicConfig.BOOKING_EVENTS_TOPIC, key, payload);

            // Propagate correlation ID via Kafka headers for distributed tracing
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                record.headers().add(new RecordHeader(
                        "X-Correlation-ID", correlationId.getBytes(StandardCharsets.UTF_8)));
            }

            // Add event metadata headers
            record.headers().add(new RecordHeader(
                    "event-type", event.eventType().getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader(
                    "aggregate-type", "Booking".getBytes(StandardCharsets.UTF_8)));

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);

            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish event to Kafka: type={}, bookingId={}, error={}",
                            event.eventType(), event.bookingId().asString(), throwable.getMessage(), throwable);
                } else {
                    log.info("Event published to Kafka: type={}, bookingId={}, topic={}, partition={}, offset={}",
                            event.eventType(),
                            event.bookingId().asString(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());

                    eventsPublishedCounter.increment();
                }
            });

        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize event: type={}, bookingId={}, error={}",
                    event.eventType(), event.bookingId().asString(), ex.getMessage(), ex);
            throw new IllegalStateException("Event serialization failed for " + event.eventType(), ex);
        }
    }
}
