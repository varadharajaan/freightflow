package com.freightflow.trackingservice.infrastructure.adapter.in.rest;

import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto.PositionUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * REST controller providing Server-Sent Events (SSE) for container position tracking.
 *
 * <p>SSE is a unidirectional, server-push protocol built on standard HTTP. Clients open
 * a long-lived HTTP connection and receive events as they occur, with automatic reconnection
 * handled by the browser's {@code EventSource} API.</p>
 *
 * <h3>SSE vs WebSocket — When to Use Each</h3>
 * <table>
 *   <tr><th>Aspect</th><th>SSE</th><th>WebSocket</th></tr>
 *   <tr><td>Direction</td><td>Server → Client only</td><td>Bidirectional</td></tr>
 *   <tr><td>Protocol</td><td>HTTP/1.1 or HTTP/2</td><td>ws:// (upgrade from HTTP)</td></tr>
 *   <tr><td>Reconnection</td><td>Built-in (EventSource)</td><td>Manual implementation</td></tr>
 *   <tr><td>Data format</td><td>Text (UTF-8)</td><td>Text or binary</td></tr>
 *   <tr><td>Firewall/Proxy</td><td>Works through HTTP proxies</td><td>May be blocked</td></tr>
 *   <tr><td>Best for</td><td>Simple dashboards, alerts</td><td>Interactive apps, chat</td></tr>
 * </table>
 *
 * <p>This endpoint is ideal for clients that only need to receive position updates
 * without sending commands back (e.g., a public tracking page). For interactive dashboards
 * requiring subscription management, use the WebSocket/STOMP endpoint instead.</p>
 *
 * @see com.freightflow.trackingservice.infrastructure.adapter.in.websocket.TrackingWebSocketHandler
 * @see PositionUpdateEvent
 */
@RestController
@RequestMapping("/api/v1/tracking")
@Profiled("sseTrackingController")
public class SseTrackingController {

    private static final Logger log = LoggerFactory.getLogger(SseTrackingController.class);

    /** SSE connection timeout: 30 minutes. */
    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    /**
     * Active SSE emitters per container ID.
     * Thread-safe: ConcurrentHashMap for container keys, CopyOnWriteArrayList for emitter lists.
     */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Opens a Server-Sent Events stream for real-time position updates of a specific container.
     *
     * <p>The client receives a long-lived HTTP connection that pushes {@link PositionUpdateEvent}
     * payloads as JSON whenever a new position is reported for the given container. The connection
     * has a 30-minute timeout and will be automatically cleaned up on completion, timeout, or error.</p>
     *
     * <h3>Client Usage (JavaScript)</h3>
     * <pre>{@code
     * const source = new EventSource('/api/v1/tracking/stream/MSCU1234567');
     * source.addEventListener('position', (event) => {
     *     const position = JSON.parse(event.data);
     *     console.log(position.latitude, position.longitude);
     * });
     * }</pre>
     *
     * @param containerId the ISO container identifier to track
     * @return an SSE emitter that streams position updates
     */
    @GetMapping(value = "/stream/{containerId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Profiled(value = "sseStreamConnect", slowThresholdMs = 100)
    public SseEmitter streamPositionUpdates(@PathVariable String containerId) {
        Objects.requireNonNull(containerId, "Container ID must not be null");
        log.info("SSE connection opened for container: {}", containerId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        List<SseEmitter> containerEmitters = emitters.computeIfAbsent(
                containerId, k -> new CopyOnWriteArrayList<>());
        containerEmitters.add(emitter);

        emitter.onCompletion(() -> {
            log.info("SSE connection completed for container: {}", containerId);
            removeEmitter(containerId, emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timed out for container: {}", containerId);
            removeEmitter(containerId, emitter);
        });

        emitter.onError(ex -> {
            log.warn("SSE connection error for container: {}, error: {}", containerId, ex.getMessage(), ex);
            removeEmitter(containerId, emitter);
        });

        return emitter;
    }

    /**
     * Sends a position update to all connected SSE clients tracking the specified container.
     *
     * <p>This method is called by the application layer (e.g., when a Kafka position update
     * event is received) to push the update to connected SSE clients. Failed emitters are
     * automatically removed from the active list.</p>
     *
     * @param event the position update to send
     */
    @Profiled(value = "sseSendPositionUpdate", slowThresholdMs = 50)
    public void sendPositionUpdate(PositionUpdateEvent event) {
        Objects.requireNonNull(event, "PositionUpdateEvent must not be null");

        List<SseEmitter> containerEmitters = emitters.get(event.containerId());
        if (containerEmitters == null || containerEmitters.isEmpty()) {
            log.debug("No SSE clients connected for container: {}", event.containerId());
            return;
        }

        log.debug("Sending SSE position update to {} client(s) for container: {}",
                containerEmitters.size(), event.containerId());

        for (SseEmitter emitter : containerEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("position")
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                log.warn("Failed to send SSE event to client for container: {}, removing emitter",
                        event.containerId());
                removeEmitter(event.containerId(), emitter);
            }
        }
    }

    private void removeEmitter(String containerId, SseEmitter emitter) {
        List<SseEmitter> containerEmitters = emitters.get(containerId);
        if (containerEmitters != null) {
            containerEmitters.remove(emitter);
            if (containerEmitters.isEmpty()) {
                emitters.remove(containerId);
            }
        }
    }
}
