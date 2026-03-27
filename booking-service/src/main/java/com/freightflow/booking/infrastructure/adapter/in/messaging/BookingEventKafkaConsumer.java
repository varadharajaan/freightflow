package com.freightflow.booking.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.booking.infrastructure.config.kafka.KafkaTopicConfig;
import com.freightflow.commons.observability.profiling.Profiled;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Kafka inbound adapter that consumes booking domain events.
 *
 * <p>Listens to the {@code booking.events} topic as a demonstration of the consumer
 * pattern. In production, this consumer would primarily listen to events from OTHER
 * bounded contexts (e.g., {@code billing.events}, {@code vessel.events}) to react
 * to cross-domain changes. The booking.events subscription serves as a self-echo
 * pattern for testing, monitoring, and CQRS read-model updates.</p>
 *
 * <h3>Consumer Group &amp; Offset Management</h3>
 * <ul>
 *   <li><b>Consumer Group:</b> {@code booking-service-group} — all booking service
 *       instances share this group. Kafka distributes partitions across instances,
 *       ensuring each message is processed by exactly one instance.</li>
 *   <li><b>Offset Commit:</b> Manual via {@link Acknowledgment#acknowledge()}.
 *       Offsets are only committed after successful processing, preventing data loss
 *       if the consumer crashes mid-processing.</li>
 *   <li><b>auto.offset.reset = earliest:</b> On first join (or expired offsets),
 *       processing starts from the beginning of the topic — no events are skipped.</li>
 * </ul>
 *
 * <h3>Dead Letter Queue (DLQ) Flow</h3>
 * <ol>
 *   <li>Message arrives from {@code booking.events} topic</li>
 *   <li>If processing fails, the {@link org.springframework.kafka.listener.DefaultErrorHandler}
 *       retries up to 3 times with a 2-second fixed backoff</li>
 *   <li>After retries are exhausted, the {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}
 *       publishes the failed message to {@code booking.events.dlq}</li>
 *   <li>Operations teams can inspect the DLQ via Kafka UI and manually replay
 *       or discard messages</li>
 * </ol>
 *
 * <h3>Correlation ID Propagation</h3>
 * <p>The {@code X-Correlation-ID} header is extracted from each Kafka record and placed
 * into the SLF4J {@link MDC}. This allows all downstream log statements to carry the
 * same correlation ID, enabling end-to-end distributed tracing across services.</p>
 *
 * @see KafkaTopicConfig
 * @see com.freightflow.booking.infrastructure.config.kafka.KafkaConsumerConfig
 * @see com.freightflow.booking.infrastructure.adapter.out.messaging.kafka.KafkaBookingEventPublisher
 */
@Component
public class BookingEventKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventKafkaConsumer.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String EVENT_TYPE_HEADER = "event-type";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ObjectMapper objectMapper;
    private final Counter eventsConsumedCounter;
    private final Counter eventsFailedCounter;

    /**
     * Constructor injection — depends on ObjectMapper for JSON deserialization and
     * MeterRegistry for Micrometer metrics.
     *
     * @param objectMapper  Jackson ObjectMapper for deserializing event payloads
     * @param meterRegistry Micrometer registry for recording consumption metrics
     */
    public BookingEventKafkaConsumer(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");

        this.eventsConsumedCounter = Counter.builder("freightflow.events.consumed")
                .description("Total domain events consumed from Kafka")
                .tag("topic", KafkaTopicConfig.BOOKING_EVENTS_TOPIC)
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("freightflow.events.consumed.failed")
                .description("Total domain events that failed processing")
                .tag("topic", KafkaTopicConfig.BOOKING_EVENTS_TOPIC)
                .register(meterRegistry);

        log.info("BookingEventKafkaConsumer initialized: topic={}, group=booking-service-group",
                KafkaTopicConfig.BOOKING_EVENTS_TOPIC);
    }

    /**
     * Consumes a booking domain event from the {@code booking.events} Kafka topic.
     *
     * <p>Extracts the correlation ID from Kafka headers, places it in MDC for distributed
     * tracing, processes the event, and manually acknowledges the offset on success.
     * On failure, the exception propagates to the configured error handler which retries
     * and eventually routes to the DLQ.</p>
     *
     * @param record         the Kafka consumer record containing the event payload
     * @param acknowledgment the manual acknowledgment handle for offset commit
     */
    @KafkaListener(
            topics = KafkaTopicConfig.BOOKING_EVENTS_TOPIC,
            groupId = "booking-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Profiled(value = "kafkaConsumeBookingEvent", slowThresholdMs = 500)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.debug("Received Kafka record: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        String correlationId = extractCorrelationId(record);
        MDC.put(MDC_CORRELATION_ID, correlationId);

        try {
            String eventType = extractHeaderValue(record, EVENT_TYPE_HEADER);

            log.info("Processing event: type={}, key={}, partition={}, offset={}, correlationId={}",
                    eventType, record.key(), record.partition(), record.offset(), correlationId);

            // Parse the event payload to validate JSON structure
            JsonNode eventNode = parseEventPayload(record.value());

            // Route event processing based on event type using Java 21 pattern matching
            processEvent(eventType, eventNode, record.key());

            // Manually acknowledge the offset — only after successful processing
            acknowledgment.acknowledge();
            eventsConsumedCounter.increment();

            log.info("Event processed successfully: type={}, key={}, correlationId={}",
                    eventType, record.key(), correlationId);

        } catch (Exception ex) {
            eventsFailedCounter.increment();
            log.error("Failed to process event: topic={}, partition={}, offset={}, key={}, correlationId={}, error={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    correlationId, ex.getMessage(), ex);

            // Re-throw to trigger the DefaultErrorHandler retry/DLQ flow
            throw new EventProcessingException(
                    "Failed to process event at offset %d on partition %d".formatted(
                            record.offset(), record.partition()), ex);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    /**
     * Parses the raw JSON event payload into a {@link JsonNode} for inspection.
     *
     * @param payload the raw JSON string
     * @return the parsed JSON tree
     * @throws EventProcessingException if the payload is not valid JSON
     */
    private JsonNode parseEventPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new EventProcessingException("Invalid JSON payload: " + ex.getMessage(), ex);
        }
    }

    /**
     * Routes event processing based on the event type.
     *
     * <p>Uses Java 21 pattern matching via switch expression on the event type string.
     * Currently handles known booking event types for logging and metrics. Future
     * implementations will delegate to domain-specific handlers (e.g., CQRS projections,
     * saga orchestrators).</p>
     *
     * @param eventType the event type extracted from the Kafka header
     * @param eventNode the parsed JSON event payload
     * @param key       the message key (bookingId)
     */
    @Profiled(value = "kafkaRouteEvent", slowThresholdMs = 200)
    private void processEvent(String eventType, JsonNode eventNode, String key) {
        switch (eventType) {
            case String type when "BookingCreated".equals(type) -> {
                log.info("Handling BookingCreated: bookingId={}", key);
                // Future: update CQRS read model, trigger notifications, etc.
            }
            case String type when "BookingConfirmed".equals(type) -> {
                log.info("Handling BookingConfirmed: bookingId={}", key);
                // Future: trigger billing workflow, notify carrier, etc.
            }
            case String type when "BookingCancelled".equals(type) -> {
                log.info("Handling BookingCancelled: bookingId={}", key);
                // Future: release capacity, process refund, etc.
            }
            case null -> {
                log.warn("Received event with null type header — processing as unknown: key={}", key);
            }
            default -> {
                log.warn("Received unknown event type: type={}, key={} — acknowledging without processing", eventType, key);
            }
        }
    }

    /**
     * Extracts the {@code X-Correlation-ID} from Kafka record headers.
     *
     * <p>If the header is missing, a new UUID is generated to ensure every log statement
     * in the processing chain has a correlation ID for traceability.</p>
     *
     * @param record the Kafka consumer record
     * @return the correlation ID string (never null)
     */
    private String extractCorrelationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(CORRELATION_ID_HEADER);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }

        String generatedId = UUID.randomUUID().toString();
        log.debug("No {} header found — generated correlationId={}", CORRELATION_ID_HEADER, generatedId);
        return generatedId;
    }

    /**
     * Extracts a string value from a Kafka record header.
     *
     * @param record     the Kafka consumer record
     * @param headerName the header key to extract
     * @return the header value, or {@code "UNKNOWN"} if the header is absent
     */
    private String extractHeaderValue(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "UNKNOWN";
    }

    /**
     * Exception wrapper for event processing failures.
     *
     * <p>Wraps the root cause to provide contextual information (offset, partition)
     * while allowing the {@link org.springframework.kafka.listener.DefaultErrorHandler}
     * to inspect the original exception for retry decisions.</p>
     */
    public static class EventProcessingException extends RuntimeException {

        /**
         * Creates an event processing exception with a message and root cause.
         *
         * @param message the contextual error message
         * @param cause   the original exception
         */
        public EventProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
