package com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto;

import java.time.Instant;

/**
 * Data Transfer Object for real-time container position updates.
 *
 * <p>This record is used by both the WebSocket (STOMP) and Server-Sent Events (SSE)
 * endpoints to push position updates to connected clients. Using a single DTO for
 * both channels ensures a consistent data format regardless of the transport chosen
 * by the client.</p>
 *
 * <h3>Transport Selection Guide</h3>
 * <ul>
 *   <li><b>WebSocket/STOMP</b>: Preferred for interactive dashboards requiring bidirectional
 *       communication (e.g., subscribe/unsubscribe commands)</li>
 *   <li><b>SSE</b>: Preferred for simple unidirectional streaming where HTTP/2 compatibility
 *       and automatic reconnection are valued</li>
 * </ul>
 *
 * @param containerId the ISO container identifier (e.g., MSCU1234567)
 * @param latitude    the latitude coordinate in decimal degrees (-90 to 90)
 * @param longitude   the longitude coordinate in decimal degrees (-180 to 180)
 * @param timestamp   when this position was recorded
 * @param source      the data source that provided this position (AIS, GPS, MANUAL)
 */
public record PositionUpdateEvent(
        String containerId,
        double latitude,
        double longitude,
        Instant timestamp,
        String source
) {
}
