package com.freightflow.trackingservice.infrastructure.adapter.in.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time container tracking updates.
 *
 * <p>Configures STOMP (Simple Text Oriented Messaging Protocol) over WebSocket for
 * bidirectional communication between the tracking service and connected clients
 * (e.g., logistics dashboards, mobile apps).</p>
 *
 * <h3>Why STOMP over plain WebSocket?</h3>
 * <ul>
 *   <li><b>Message semantics</b>: STOMP provides publish/subscribe semantics with destinations,
 *       headers, and body — similar to JMS/AMQP but over WebSocket</li>
 *   <li><b>Spring integration</b>: Spring's {@code @MessageMapping} and {@code SimpMessagingTemplate}
 *       provide a controller-like programming model for WebSocket handlers</li>
 *   <li><b>Topic-based routing</b>: Clients subscribe to {@code /topic/tracking/{containerId}}
 *       and receive only updates for containers they care about</li>
 *   <li><b>SockJS fallback</b>: Provides graceful degradation to HTTP long-polling for clients
 *       behind restrictive firewalls or older browsers</li>
 * </ul>
 *
 * <h3>Endpoint Layout</h3>
 * <ul>
 *   <li>{@code /ws/tracking} — WebSocket handshake endpoint (SockJS-enabled)</li>
 *   <li>{@code /topic/*} — subscription prefix for server-to-client messages</li>
 *   <li>{@code /app/*} — prefix for client-to-server messages (handled by {@code @MessageMapping})</li>
 * </ul>
 *
 * @see TrackingWebSocketHandler
 */
@Configuration
@EnableWebSocketMessageBroker
public class TrackingWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Registers the STOMP WebSocket endpoint with SockJS fallback.
     *
     * <p>Clients connect to {@code ws://host:port/ws/tracking} for native WebSocket
     * or {@code http://host:port/ws/tracking} for SockJS fallback. The {@code setAllowedOriginPatterns("*")}
     * is permissive for development; production deployments should restrict to known origins.</p>
     *
     * @param registry the STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/tracking")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Configures the message broker for topic-based routing.
     *
     * <p>The simple in-memory broker handles subscriptions to {@code /topic/*} destinations.
     * For production clusters, this can be replaced with a full-featured broker (RabbitMQ/ActiveMQ)
     * via {@code enableStompBrokerRelay} to support multi-instance fanout.</p>
     *
     * @param registry the message broker registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
