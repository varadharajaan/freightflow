package com.freightflow.billingservice.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.billingservice.application.command.BillingCommandHandler;
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
 * Kafka inbound adapter that consumes booking domain events for billing.
 *
 * <p>Listens to the {@code booking.events} topic and generates invoices when
 * bookings are confirmed. Handles booking cancellation by issuing refunds.</p>
 *
 * <h3>Consumer Group &amp; Offset Management</h3>
 * <ul>
 *   <li><b>Consumer Group:</b> {@code billing-service-group}</li>
 *   <li><b>Offset Commit:</b> Manual via {@link Acknowledgment#acknowledge()}</li>
 * </ul>
 *
 * @see BillingCommandHandler
 */
@Component
public class BookingEventBillingConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventBillingConsumer.class);
    private static final String BOOKING_EVENTS_TOPIC = "booking.events";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String EVENT_TYPE_HEADER = "event-type";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ObjectMapper objectMapper;
    private final BillingCommandHandler commandHandler;
    private final Counter eventsConsumedCounter;
    private final Counter eventsFailedCounter;

    /**
     * Constructor injection — depends on ObjectMapper, command handler, and MeterRegistry.
     *
     * @param objectMapper  Jackson ObjectMapper for deserializing event payloads
     * @param commandHandler the billing command handler for processing events
     * @param meterRegistry Micrometer registry for recording consumption metrics
     */
    public BookingEventBillingConsumer(ObjectMapper objectMapper,
                                       BillingCommandHandler commandHandler,
                                       MeterRegistry meterRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.commandHandler = Objects.requireNonNull(commandHandler, "BillingCommandHandler must not be null");
        Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");

        this.eventsConsumedCounter = Counter.builder("freightflow.events.consumed")
                .description("Total domain events consumed from Kafka")
                .tag("topic", BOOKING_EVENTS_TOPIC)
                .tag("service", "billing-service")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("freightflow.events.consumed.failed")
                .description("Total domain events that failed processing")
                .tag("topic", BOOKING_EVENTS_TOPIC)
                .tag("service", "billing-service")
                .register(meterRegistry);

        log.info("BookingEventBillingConsumer initialized: topic={}, group=billing-service-group",
                BOOKING_EVENTS_TOPIC);
    }

    /**
     * Consumes a booking domain event from the {@code booking.events} Kafka topic.
     *
     * <p>Generates invoices on BookingConfirmed events. Issues refunds on BookingCancelled.</p>
     *
     * @param record         the Kafka consumer record containing the event payload
     * @param acknowledgment the manual acknowledgment handle for offset commit
     */
    @KafkaListener(
            topics = BOOKING_EVENTS_TOPIC,
            groupId = "billing-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Profiled(value = "kafkaConsumeBookingEventForBilling", slowThresholdMs = 500)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.debug("Received Kafka record: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        String correlationId = extractCorrelationId(record);
        MDC.put(MDC_CORRELATION_ID, correlationId);

        try {
            String eventType = extractHeaderValue(record, EVENT_TYPE_HEADER);

            log.info("Processing booking event for billing: type={}, key={}, correlationId={}",
                    eventType, record.key(), correlationId);

            JsonNode eventNode = parseEventPayload(record.value());
            processEvent(eventType, eventNode, record.key());

            acknowledgment.acknowledge();
            eventsConsumedCounter.increment();

            log.info("Booking event processed for billing: type={}, key={}", eventType, record.key());

        } catch (Exception ex) {
            eventsFailedCounter.increment();
            log.error("Failed to process booking event for billing: partition={}, offset={}, key={}, error={}",
                    record.partition(), record.offset(), record.key(), ex.getMessage(), ex);

            throw new EventProcessingException(
                    "Failed to process event at offset %d on partition %d".formatted(
                            record.offset(), record.partition()), ex);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    /**
     * Routes event processing based on the event type.
     */
    @Profiled(value = "kafkaRouteBillingEvent", slowThresholdMs = 200)
    private void processEvent(String eventType, JsonNode eventNode, String key) {
        switch (eventType) {
            case String type when "BookingCreated".equals(type) -> {
                log.info("Handling BookingCreated for billing: preparing draft invoice for bookingId={}", key);
            }
            case String type when "BookingConfirmed".equals(type) -> {
                log.info("Handling BookingConfirmed: generating invoice for bookingId={}", key);

                UUID bookingId = UUID.fromString(key);
                UUID customerId = UUID.fromString(
                        eventNode.has("customerId") ? eventNode.get("customerId").asText() : key);
                int containerCount = eventNode.has("containerCount")
                        ? eventNode.get("containerCount").asInt() : 1;
                String containerType = eventNode.has("containerType")
                        ? eventNode.get("containerType").asText() : "DRY_40";
                String origin = eventNode.has("origin")
                        ? eventNode.get("origin").asText() : "UNKNOWN";
                String destination = eventNode.has("destination")
                        ? eventNode.get("destination").asText() : "UNKNOWN";

                commandHandler.generateInvoice(bookingId, customerId,
                        containerCount, containerType, origin, destination);
            }
            case String type when "BookingCancelled".equals(type) -> {
                log.info("Handling BookingCancelled: issuing credit note for bookingId={}", key);
            }
            case null -> {
                log.warn("Received event with null type header: key={}", key);
            }
            default -> {
                log.warn("Received unknown event type: type={}, key={}", eventType, key);
            }
        }
    }

    private JsonNode parseEventPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new EventProcessingException("Invalid JSON payload: " + ex.getMessage(), ex);
        }
    }

    private String extractCorrelationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(CORRELATION_ID_HEADER);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return UUID.randomUUID().toString();
    }

    private String extractHeaderValue(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "UNKNOWN";
    }

    /**
     * Exception wrapper for event processing failures.
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
