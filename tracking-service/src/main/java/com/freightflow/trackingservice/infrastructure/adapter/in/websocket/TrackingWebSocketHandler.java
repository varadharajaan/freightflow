package com.freightflow.trackingservice.infrastructure.adapter.in.websocket;

import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto.PositionUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Objects;

/**
 * STOMP WebSocket controller for real-time container tracking updates.
 *
 * <p>This controller handles two responsibilities:</p>
 * <ol>
 *   <li><b>Client subscriptions</b>: Receives subscription requests via {@code @MessageMapping}
 *       when clients want to start tracking a specific container</li>
 *   <li><b>Push updates</b>: Uses {@link SimpMessagingTemplate} to broadcast position updates
 *       to all clients subscribed to a specific container's topic</li>
 * </ol>
 *
 * <h3>Topic Structure</h3>
 * <p>Position updates are pushed to {@code /topic/tracking/{containerId}}, allowing clients
 * to subscribe to individual containers. This avoids broadcasting all updates to all clients,
 * reducing bandwidth and processing on the client side.</p>
 *
 * <h3>Usage from server-side code</h3>
 * <pre>{@code
 * trackingWebSocketHandler.broadcastPositionUpdate(new PositionUpdateEvent(
 *     "MSCU1234567", 51.9244, 4.4777, Instant.now(), "AIS"
 * ));
 * }</pre>
 *
 * @see TrackingWebSocketConfig
 * @see PositionUpdateEvent
 */
@Controller
@Profiled("trackingWebSocket")
public class TrackingWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TrackingWebSocketHandler.class);

    private static final String TRACKING_TOPIC_PREFIX = "/topic/tracking/";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Constructs the handler with the Spring STOMP messaging template.
     *
     * @param messagingTemplate the template for sending messages to STOMP destinations
     */
    public TrackingWebSocketHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate,
                "SimpMessagingTemplate must not be null");
        log.info("TrackingWebSocketHandler initialized");
    }

    /**
     * Handles client subscription requests for container tracking updates.
     *
     * <p>When a client sends a message to {@code /app/tracking/subscribe} with a container ID
     * payload, this logs the subscription intent. The actual subscription is managed by the
     * STOMP broker when the client subscribes to {@code /topic/tracking/{containerId}}.</p>
     *
     * @param containerId the container identifier to subscribe to
     */
    @MessageMapping("/tracking/subscribe")
    @Profiled(value = "trackingSubscribe", slowThresholdMs = 100)
    public void handleSubscription(@Payload String containerId) {
        log.info("Client subscription request received for container: {}", containerId);
    }

    /**
     * Broadcasts a position update to all clients subscribed to the container's tracking topic.
     *
     * <p>This method is called by the application layer (e.g., when a Kafka position update
     * event is received) to push the update to connected WebSocket clients.</p>
     *
     * @param event the position update to broadcast
     */
    @Profiled(value = "broadcastPositionUpdate", slowThresholdMs = 50)
    public void broadcastPositionUpdate(PositionUpdateEvent event) {
        Objects.requireNonNull(event, "PositionUpdateEvent must not be null");
        String destination = TRACKING_TOPIC_PREFIX + event.containerId();

        log.debug("Broadcasting position update to {}: lat={}, lon={}, source={}",
                destination, event.latitude(), event.longitude(), event.source());

        messagingTemplate.convertAndSend(destination, event);

        log.debug("Position update broadcast completed for container: {}", event.containerId());
    }
}
