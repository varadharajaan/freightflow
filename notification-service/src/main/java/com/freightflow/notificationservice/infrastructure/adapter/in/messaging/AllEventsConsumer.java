package com.freightflow.notificationservice.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.commons.domain.FreightFlowConstants;
import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.notificationservice.application.command.NotificationCommandHandler;
import com.freightflow.notificationservice.domain.model.NotificationChannel;
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
 * Kafka inbound adapter that consumes events from ALL FreightFlow services.
 *
 * <p>This is a <b>PURE CONSUMER</b> — the notification service does not produce events
 * to any Kafka topic. It listens to events from booking, billing, and tracking services
 * to trigger notification delivery based on domain events.</p>
 *
 * <h3>Subscribed Topics</h3>
 * <ul>
 *   <li>{@code booking.events} — BookingCreated, BookingConfirmed, BookingCancelled</li>
 *   <li>{@code billing.events} — InvoiceGenerated, PaymentReceived, RefundIssued</li>
 *   <li>{@code tracking.events} — ContainerMoved, milestone alerts</li>
 * </ul>
 *
 * <h3>Event Routing via Pattern Matching</h3>
 * <p>Uses Java 21 pattern matching switch on the event type header to route each
 * event to the appropriate notification template and channel. Unknown events are
 * acknowledged without processing.</p>
 *
 * <h3>Consumer Group &amp; Offset Management</h3>
 * <ul>
 *   <li><b>Consumer Group:</b> {@code notification-service-group}</li>
 *   <li><b>Offset Commit:</b> Manual via {@link Acknowledgment#acknowledge()}</li>
 *   <li><b>auto.offset.reset:</b> earliest — no events are skipped on first join</li>
 * </ul>
 *
 * @see NotificationCommandHandler
 * @see FreightFlowConstants
 */
@Component
public class AllEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AllEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationCommandHandler commandHandler;

    /**
     * Constructor injection — depends on ObjectMapper for JSON deserialization and
     * the command handler for creating and sending notifications.
     *
     * @param objectMapper   Jackson ObjectMapper for deserializing event payloads
     * @param commandHandler the notification command handler for sending notifications
     */
    public AllEventsConsumer(ObjectMapper objectMapper,
                              NotificationCommandHandler commandHandler) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.commandHandler = Objects.requireNonNull(commandHandler,
                "NotificationCommandHandler must not be null");

        log.info("AllEventsConsumer initialized: topics=[{}, {}, {}], group={}",
                FreightFlowConstants.TOPIC_BOOKING_EVENTS,
                FreightFlowConstants.TOPIC_BILLING_EVENTS,
                FreightFlowConstants.TOPIC_TRACKING_EVENTS,
                FreightFlowConstants.CONSUMER_GROUP_NOTIFICATION);
    }

    /**
     * Consumes events from booking, billing, and tracking topics.
     *
     * <p>This single listener handles all three topics. Event routing is performed
     * by extracting the event type header and using pattern matching to dispatch
     * to the appropriate notification builder.</p>
     *
     * @param record         the Kafka consumer record containing the event payload
     * @param acknowledgment the manual acknowledgment handle for offset commit
     */
    @KafkaListener(
            topics = {
                    FreightFlowConstants.TOPIC_BOOKING_EVENTS,
                    FreightFlowConstants.TOPIC_BILLING_EVENTS,
                    FreightFlowConstants.TOPIC_TRACKING_EVENTS
            },
            groupId = FreightFlowConstants.CONSUMER_GROUP_NOTIFICATION,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Profiled(value = "kafkaConsumeAllEvents", slowThresholdMs = 1000)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.debug("Received Kafka record: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        String correlationId = extractCorrelationId(record);
        MDC.put(FreightFlowConstants.MDC_CORRELATION_ID, correlationId);

        try {
            String eventType = extractHeaderValue(record, FreightFlowConstants.KAFKA_HEADER_EVENT_TYPE);

            log.info("Processing event for notification: topic={}, type={}, key={}, correlationId={}",
                    record.topic(), eventType, record.key(), correlationId);

            JsonNode eventNode = parseEventPayload(record.value());
            routeEvent(eventType, eventNode, record.topic());

            acknowledgment.acknowledge();

            log.info("Event processed for notification: topic={}, type={}, key={}, correlationId={}",
                    record.topic(), eventType, record.key(), correlationId);

        } catch (Exception ex) {
            log.error("Failed to process event for notification: topic={}, partition={}, offset={}, "
                            + "key={}, correlationId={}, error={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    correlationId, ex.getMessage(), ex);
            throw new RuntimeException(
                    "Failed to process event at offset %d on partition %d from topic %s".formatted(
                            record.offset(), record.partition(), record.topic()), ex);
        } finally {
            MDC.remove(FreightFlowConstants.MDC_CORRELATION_ID);
        }
    }

    /**
     * Routes events to notification builders using Java 21 pattern matching switch.
     *
     * <p>Each event type maps to a specific notification template and channel.
     * Unknown events are logged and acknowledged without triggering notifications.</p>
     *
     * @param eventType the event type from the Kafka header
     * @param eventNode the parsed JSON event payload
     * @param topic     the originating Kafka topic
     */
    @Profiled(value = "kafkaRouteEvent", slowThresholdMs = 500)
    private void routeEvent(String eventType, JsonNode eventNode, String topic) {
        switch (eventType) {
            // ── Booking Events ──
            case String type when "BookingCreated".equals(type) -> {
                log.info("Routing BookingCreated → send booking confirmation email");
                sendBookingNotification(eventNode,
                        "Booking Received",
                        "Your booking has been received and is pending confirmation.");
            }
            case String type when "BookingConfirmed".equals(type) -> {
                log.info("Routing BookingConfirmed → send confirmation email");
                sendBookingNotification(eventNode,
                        "Booking Confirmed",
                        "Your booking has been confirmed and assigned to a voyage.");
            }
            case String type when "BookingCancelled".equals(type) -> {
                log.info("Routing BookingCancelled → send cancellation email");
                String reason = eventNode.path("reason").asText("No reason provided");
                sendBookingNotification(eventNode,
                        "Booking Cancelled",
                        "Your booking has been cancelled. Reason: " + reason);
            }

            // ── Billing Events ──
            case String type when "InvoiceGenerated".equals(type) -> {
                log.info("Routing InvoiceGenerated → send invoice email");
                sendBillingNotification(eventNode,
                        "Invoice Generated",
                        "A new invoice has been generated for your booking.");
            }
            case String type when "PaymentReceived".equals(type) -> {
                log.info("Routing PaymentReceived → send payment receipt");
                sendBillingNotification(eventNode,
                        "Payment Received",
                        "Your payment has been received and processed. Thank you.");
            }
            case String type when "RefundIssued".equals(type) -> {
                log.info("Routing RefundIssued → send refund confirmation");
                sendBillingNotification(eventNode,
                        "Refund Issued",
                        "A refund has been issued to your account.");
            }

            // ── Tracking Events ──
            case String type when "ContainerMoved".equals(type) -> {
                log.info("Routing ContainerMoved → send milestone alert");
                sendTrackingNotification(eventNode,
                        "Container Update",
                        "Your container has reached a new milestone.");
            }

            // ── Vessel Events ──
            case String type when "VoyageDeparted".equals(type) -> {
                log.info("Routing VoyageDeparted → send departure notification");
                sendGenericNotification(eventNode,
                        "Voyage Departed",
                        "The vessel carrying your cargo has departed.");
            }
            case String type when "VoyageArrived".equals(type) -> {
                log.info("Routing VoyageArrived → send arrival notification");
                sendGenericNotification(eventNode,
                        "Voyage Arrived",
                        "The vessel carrying your cargo has arrived at the destination port.");
            }

            case null -> {
                log.warn("Received event with null type header from topic={} — skipping", topic);
            }
            default -> {
                log.debug("Received unhandled event type: type={}, topic={} — acknowledging", eventType, topic);
            }
        }
    }

    /**
     * Sends a notification for a booking-related event.
     *
     * @param eventNode the parsed event JSON
     * @param subject   the notification subject
     * @param body      the notification body
     */
    private void sendBookingNotification(JsonNode eventNode, String subject, String body) {
        String customerId = extractCustomerId(eventNode);
        String email = eventNode.path("email").asText("noreply@freightflow.com");

        commandHandler.sendNotification(
                UUID.fromString(customerId),
                new NotificationChannel.EmailChannel(email),
                subject,
                body
        );
    }

    /**
     * Sends a notification for a billing-related event.
     *
     * @param eventNode the parsed event JSON
     * @param subject   the notification subject
     * @param body      the notification body
     */
    private void sendBillingNotification(JsonNode eventNode, String subject, String body) {
        String customerId = extractCustomerId(eventNode);
        String email = eventNode.path("email").asText("noreply@freightflow.com");

        commandHandler.sendNotification(
                UUID.fromString(customerId),
                new NotificationChannel.EmailChannel(email),
                subject,
                body
        );
    }

    /**
     * Sends a notification for a tracking-related event.
     *
     * @param eventNode the parsed event JSON
     * @param subject   the notification subject
     * @param body      the notification body
     */
    private void sendTrackingNotification(JsonNode eventNode, String subject, String body) {
        String customerId = extractCustomerId(eventNode);
        String email = eventNode.path("email").asText("noreply@freightflow.com");

        commandHandler.sendNotification(
                UUID.fromString(customerId),
                new NotificationChannel.EmailChannel(email),
                subject,
                body
        );
    }

    /**
     * Sends a generic notification for events without customer-specific routing.
     *
     * @param eventNode the parsed event JSON
     * @param subject   the notification subject
     * @param body      the notification body
     */
    private void sendGenericNotification(JsonNode eventNode, String subject, String body) {
        String recipientId = eventNode.has("customerId")
                ? extractCustomerId(eventNode)
                : UUID.randomUUID().toString();
        String email = eventNode.path("email").asText("noreply@freightflow.com");

        commandHandler.sendNotification(
                UUID.fromString(recipientId),
                new NotificationChannel.EmailChannel(email),
                subject,
                body
        );
    }

    /**
     * Extracts the customer ID from the event payload.
     *
     * <p>Handles both flat ({@code "customerId": "uuid"}) and nested
     * ({@code "customerId": {"value": "uuid"}}) formats.</p>
     *
     * @param eventNode the parsed event JSON
     * @return the customer ID string
     */
    private String extractCustomerId(JsonNode eventNode) {
        JsonNode customerIdNode = eventNode.path("customerId");
        if (customerIdNode.isObject()) {
            return customerIdNode.path("value").asText(UUID.randomUUID().toString());
        }
        return customerIdNode.asText(UUID.randomUUID().toString());
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
