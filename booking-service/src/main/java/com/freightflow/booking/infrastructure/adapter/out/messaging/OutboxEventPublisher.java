package com.freightflow.booking.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.booking.domain.port.BookingEventPublisher;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Outbox-based implementation of {@link BookingEventPublisher} that writes domain events
 * to the {@code outbox_events} table within the same database transaction as the aggregate
 * state change.
 *
 * <p><b>Why the Outbox Pattern?</b></p>
 * <p>The naive "dual-write" approach — persisting the aggregate and then publishing to Kafka —
 * is inherently unreliable. If the application crashes after the database commit but before
 * the Kafka send, the event is lost. Conversely, if Kafka succeeds but the database commit
 * fails, downstream consumers see an event for a state change that never happened.</p>
 *
 * <p>The Outbox Pattern solves this by writing the event to a database table in the
 * <b>same ACID transaction</b> as the aggregate state change. A separate
 * {@link OutboxProcessor} reads unprocessed events and forwards them to Kafka.
 * This guarantees at-least-once delivery with no dual-write risk.</p>
 *
 * <p>This implementation uses {@link JdbcTemplate} for direct SQL insertion into the
 * {@code outbox_events} table, and {@link ObjectMapper} for JSON serialization of
 * event payloads. Metadata (correlationId, timestamp) is captured from the MDC
 * context for distributed tracing.</p>
 *
 * @see BookingEventPublisher
 * @see OutboxProcessor
 */
@Component
public class OutboxEventPublisher implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private static final String INSERT_SQL = """
            INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, metadata, created_at)
            VALUES (?, ?::uuid, ?, ?::jsonb, ?::jsonb, ?)
            """;

    private static final String AGGREGATE_TYPE = "Booking";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection — depends on JdbcTemplate for transactional writes and
     * ObjectMapper for JSON serialization.
     *
     * @param jdbcTemplate  the JDBC template for database operations (must not be null)
     * @param objectMapper  the Jackson ObjectMapper for JSON serialization (must not be null)
     */
    public OutboxEventPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }

    /**
     * Writes a domain event to the outbox table within the current transaction.
     *
     * <p>The event is serialized to JSON and inserted into {@code outbox_events}
     * with {@code processed = false}. The {@link OutboxProcessor} will pick it up
     * asynchronously and forward it to Kafka.</p>
     *
     * @param event the domain event to persist to the outbox
     */
    @Override
    @Profiled(value = "outboxEventPublisher.publish", slowThresholdMs = 100)
    public void publish(BookingEvent event) {
        log.debug("Writing event to outbox: type={}, bookingId={}, eventId={}",
                event.eventType(), event.bookingId().asString(), event.eventId());

        try {
            String payload = objectMapper.writeValueAsString(event);
            String metadata = objectMapper.writeValueAsString(buildMetadata(event));

            jdbcTemplate.update(INSERT_SQL,
                    AGGREGATE_TYPE,
                    event.bookingId().asString(),
                    event.eventType(),
                    payload,
                    metadata,
                    Instant.now()
            );

            log.info("Event written to outbox: type={}, bookingId={}, eventId={}",
                    event.eventType(), event.bookingId().asString(), event.eventId());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event to JSON: type={}, bookingId={}, eventId={}",
                    event.eventType(), event.bookingId().asString(), event.eventId(), e);
            throw new IllegalStateException("Event serialization failed for event " + event.eventId(), e);
        }
    }

    /**
     * Builds metadata map from the current MDC context and event properties.
     *
     * @param event the domain event
     * @return a metadata map containing correlationId, timestamp, and event source
     */
    private Map<String, Object> buildMetadata(BookingEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("eventId", event.eventId().toString());

        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            metadata.put("correlationId", correlationId);
        }

        String traceId = MDC.get("traceId");
        if (traceId != null) {
            metadata.put("traceId", traceId);
        }

        return metadata;
    }
}
