package com.freightflow.customerservice.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.commons.domain.FreightFlowConstants;
import com.freightflow.commons.domain.Money;
import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.customerservice.application.command.CustomerCommandHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Kafka inbound adapter that consumes booking domain events for credit operations.
 *
 * <p>Listens to the {@code booking.events} topic and reacts to booking lifecycle changes
 * that affect customer credit:</p>
 * <ul>
 *   <li>{@code BookingConfirmed} → allocate credit for the confirmed booking amount</li>
 *   <li>{@code BookingCancelled} → release previously allocated credit</li>
 * </ul>
 *
 * <h3>Consumer Group &amp; Offset Management</h3>
 * <ul>
 *   <li><b>Consumer Group:</b> {@code customer-service-group} — all customer service
 *       instances share this group for partition-level load balancing.</li>
 *   <li><b>Offset Commit:</b> Manual via {@link Acknowledgment#acknowledge()}.
 *       Offsets are only committed after successful processing.</li>
 * </ul>
 *
 * <h3>Correlation ID Propagation</h3>
 * <p>The {@code X-Correlation-ID} header is extracted from each Kafka record and placed
 * into the SLF4J {@link MDC} for distributed tracing.</p>
 *
 * @see CustomerCommandHandler
 * @see FreightFlowConstants
 */
@Component
public class BookingEventCustomerConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventCustomerConsumer.class);

    private final ObjectMapper objectMapper;
    private final CustomerCommandHandler commandHandler;

    /**
     * Constructor injection — depends on ObjectMapper for JSON deserialization and
     * the command handler for executing credit operations.
     *
     * @param objectMapper   Jackson ObjectMapper for deserializing event payloads
     * @param commandHandler the customer command handler for credit operations
     */
    public BookingEventCustomerConsumer(ObjectMapper objectMapper,
                                         CustomerCommandHandler commandHandler) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.commandHandler = Objects.requireNonNull(commandHandler,
                "CustomerCommandHandler must not be null");

        log.info("BookingEventCustomerConsumer initialized: topic={}, group=customer-service-group",
                FreightFlowConstants.TOPIC_BOOKING_EVENTS);
    }

    /**
     * Consumes a booking domain event from the {@code booking.events} Kafka topic.
     *
     * <p>Routes events to the appropriate credit operation based on event type.
     * On success, the offset is manually acknowledged.</p>
     *
     * @param record         the Kafka consumer record containing the event payload
     * @param acknowledgment the manual acknowledgment handle for offset commit
     */
    @KafkaListener(
            topics = FreightFlowConstants.TOPIC_BOOKING_EVENTS,
            groupId = "customer-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Profiled(value = "kafkaConsumeBookingEvent", slowThresholdMs = 500)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.debug("Received Kafka record: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        String correlationId = extractCorrelationId(record);
        MDC.put(FreightFlowConstants.MDC_CORRELATION_ID, correlationId);

        try {
            String eventType = extractHeaderValue(record, FreightFlowConstants.KAFKA_HEADER_EVENT_TYPE);

            log.info("Processing booking event: type={}, key={}, partition={}, offset={}, correlationId={}",
                    eventType, record.key(), record.partition(), record.offset(), correlationId);

            JsonNode eventNode = parseEventPayload(record.value());
            processEvent(eventType, eventNode);

            acknowledgment.acknowledge();

            log.info("Booking event processed successfully: type={}, key={}, correlationId={}",
                    eventType, record.key(), correlationId);

        } catch (Exception ex) {
            log.error("Failed to process booking event: topic={}, partition={}, offset={}, key={}, "
                            + "correlationId={}, error={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    correlationId, ex.getMessage(), ex);
            throw new RuntimeException(
                    "Failed to process booking event at offset %d on partition %d".formatted(
                            record.offset(), record.partition()), ex);
        } finally {
            MDC.remove(FreightFlowConstants.MDC_CORRELATION_ID);
        }
    }

    /**
     * Routes event processing based on event type using Java 21 pattern matching.
     *
     * @param eventType the event type extracted from the Kafka header
     * @param eventNode the parsed JSON event payload
     */
    @Profiled(value = "kafkaRouteBookingEvent", slowThresholdMs = 200)
    private void processEvent(String eventType, JsonNode eventNode) {
        switch (eventType) {
            case String type when "BookingConfirmed".equals(type) -> handleBookingConfirmed(eventNode);
            case String type when "BookingCancelled".equals(type) -> handleBookingCancelled(eventNode);
            case String type when "BookingCreated".equals(type) -> {
                log.info("BookingCreated received — no credit action required");
            }
            case null -> {
                log.warn("Received event with null type header — skipping");
            }
            default -> {
                log.warn("Received unknown event type: {} — acknowledging without processing", eventType);
            }
        }
    }

    /**
     * Handles BookingConfirmed events by allocating credit for the customer.
     *
     * @param eventNode the parsed event payload
     */
    private void handleBookingConfirmed(JsonNode eventNode) {
        String customerId = eventNode.path("customerId").path("value").asText();
        BigDecimal estimatedCost = eventNode.has("estimatedCost")
                ? new BigDecimal(eventNode.path("estimatedCost").asText("0"))
                : BigDecimal.valueOf(1000);

        log.info("Allocating credit for BookingConfirmed: customerId={}, amount={}",
                customerId, estimatedCost);

        commandHandler.allocateCredit(customerId, Money.of(estimatedCost, "USD"));
    }

    /**
     * Handles BookingCancelled events by releasing previously allocated credit.
     *
     * @param eventNode the parsed event payload
     */
    private void handleBookingCancelled(JsonNode eventNode) {
        String customerId = eventNode.path("customerId").path("value").asText();
        BigDecimal estimatedCost = eventNode.has("estimatedCost")
                ? new BigDecimal(eventNode.path("estimatedCost").asText("0"))
                : BigDecimal.valueOf(1000);

        log.info("Releasing credit for BookingCancelled: customerId={}, amount={}",
                customerId, estimatedCost);

        commandHandler.releaseCredit(customerId, Money.of(estimatedCost, "USD"));
    }

    /**
     * Parses the raw JSON event payload into a {@link JsonNode} for inspection.
     *
     * @param payload the raw JSON string
     * @return the parsed JSON tree
     */
    private JsonNode parseEventPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Invalid JSON payload: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extracts the {@code X-Correlation-ID} from Kafka record headers.
     *
     * @param record the Kafka consumer record
     * @return the correlation ID string (never null)
     */
    private String extractCorrelationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(FreightFlowConstants.KAFKA_HEADER_CORRELATION_ID);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        String generatedId = UUID.randomUUID().toString();
        log.debug("No correlation ID header found — generated correlationId={}", generatedId);
        return generatedId;
    }

    /**
     * Extracts a string value from a Kafka record header.
     *
     * @param record     the Kafka consumer record
     * @param headerName the header key to extract
     * @return the header value, or {@code "UNKNOWN"} if absent
     */
    private String extractHeaderValue(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "UNKNOWN";
    }
}
