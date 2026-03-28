package com.freightflow.vesselschedule.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.vesselschedule.application.command.VesselCommandHandler;
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
 * Kafka inbound adapter that consumes booking domain events for capacity management.
 *
 * <p>Listens to the {@code booking.events} topic and reserves vessel capacity when
 * bookings are confirmed. Releases capacity when bookings are cancelled.</p>
 *
 * <h3>Consumer Group &amp; Offset Management</h3>
 * <ul>
 *   <li><b>Consumer Group:</b> {@code vessel-schedule-service-group}</li>
 *   <li><b>Offset Commit:</b> Manual via {@link Acknowledgment#acknowledge()}</li>
 * </ul>
 *
 * @see VesselCommandHandler
 */
@Component
public class BookingEventVesselConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventVesselConsumer.class);
    private static final String BOOKING_EVENTS_TOPIC = "booking.events";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String EVENT_TYPE_HEADER = "event-type";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ObjectMapper objectMapper;
    private final VesselCommandHandler commandHandler;
    private final Counter eventsConsumedCounter;
    private final Counter eventsFailedCounter;

    /**
     * Constructor injection — depends on ObjectMapper, command handler, and MeterRegistry.
     *
     * @param objectMapper  Jackson ObjectMapper for deserializing event payloads
     * @param commandHandler the vessel command handler for processing events
     * @param meterRegistry Micrometer registry for recording consumption metrics
     */
    public BookingEventVesselConsumer(ObjectMapper objectMapper,
                                      VesselCommandHandler commandHandler,
                                      MeterRegistry meterRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.commandHandler = Objects.requireNonNull(commandHandler, "VesselCommandHandler must not be null");
        Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");

        this.eventsConsumedCounter = Counter.builder("freightflow.events.consumed")
                .description("Total domain events consumed from Kafka")
                .tag("topic", BOOKING_EVENTS_TOPIC)
                .tag("service", "vessel-schedule-service")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("freightflow.events.consumed.failed")
                .description("Total domain events that failed processing")
                .tag("topic", BOOKING_EVENTS_TOPIC)
                .tag("service", "vessel-schedule-service")
                .register(meterRegistry);

        log.info("BookingEventVesselConsumer initialized: topic={}, group=vessel-schedule-service-group",
                BOOKING_EVENTS_TOPIC);
    }

    /**
     * Consumes a booking domain event from the {@code booking.events} Kafka topic.
     *
     * <p>Reserves capacity on BookingConfirmed events. Releases capacity on BookingCancelled.</p>
     *
     * @param record         the Kafka consumer record containing the event payload
     * @param acknowledgment the manual acknowledgment handle for offset commit
     */
    @KafkaListener(
            topics = BOOKING_EVENTS_TOPIC,
            groupId = "vessel-schedule-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Profiled(value = "kafkaConsumeBookingEventForVessel", slowThresholdMs = 500)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.debug("Received Kafka record: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        String correlationId = extractCorrelationId(record);
        MDC.put(MDC_CORRELATION_ID, correlationId);

        try {
            String eventType = extractHeaderValue(record, EVENT_TYPE_HEADER);

            log.info("Processing booking event for vessel scheduling: type={}, key={}, correlationId={}",
                    eventType, record.key(), correlationId);

            JsonNode eventNode = parseEventPayload(record.value());
            processEvent(eventType, eventNode, record.key());

            acknowledgment.acknowledge();
            eventsConsumedCounter.increment();

            log.info("Booking event processed for vessel scheduling: type={}, key={}",
                    eventType, record.key());

        } catch (Exception ex) {
            eventsFailedCounter.increment();
            log.error("Failed to process booking event for vessel: partition={}, offset={}, key={}, error={}",
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
    @Profiled(value = "kafkaRouteVesselEvent", slowThresholdMs = 200)
    private void processEvent(String eventType, JsonNode eventNode, String key) {
        switch (eventType) {
            case String type when "BookingConfirmed".equals(type) -> {
                log.info("Handling BookingConfirmed: reserving capacity for bookingId={}", key);

                if (eventNode.has("voyageId") && eventNode.has("containerCount")) {
                    UUID voyageId = UUID.fromString(eventNode.get("voyageId").asText());
                    UUID bookingId = UUID.fromString(key);
                    int containerCount = eventNode.get("containerCount").asInt();

                    commandHandler.reserveCapacity(voyageId, bookingId, containerCount);
                } else {
                    log.warn("BookingConfirmed event missing voyageId or containerCount: bookingId={}", key);
                }
            }
            case String type when "BookingCancelled".equals(type) -> {
                log.info("Handling BookingCancelled: releasing capacity for bookingId={}", key);

                if (eventNode.has("voyageId") && eventNode.has("containerCount")) {
                    UUID voyageId = UUID.fromString(eventNode.get("voyageId").asText());
                    int containerCount = eventNode.get("containerCount").asInt();

                    commandHandler.releaseCapacity(voyageId, containerCount);
                }
            }
            case null -> {
                log.warn("Received event with null type header: key={}", key);
            }
            default -> {
                log.debug("Ignoring event type: type={}, key={}", eventType, key);
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
