package com.freightflow.booking.infrastructure.adapter.out.messaging;

import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Polling-based outbox processor that reads unprocessed events from the
 * {@code outbox_events} table and forwards them to Kafka.
 *
 * <p><b>Polling Publisher vs. Debezium CDC</b></p>
 * <p>There are two primary approaches for draining an outbox table:</p>
 * <ol>
 *   <li><b>Polling Publisher</b> (this implementation) — a scheduled task queries for
 *       unprocessed rows and publishes them. Simple to implement, no additional
 *       infrastructure required. Trade-off: slight latency (up to the poll interval)
 *       and requires careful batch sizing to avoid overloading Kafka.</li>
 *   <li><b>Debezium CDC</b> — a Change Data Capture connector tails the database WAL
 *       (Write-Ahead Log) and streams new outbox rows to Kafka in near-real-time.
 *       Lower latency and no polling overhead, but requires Kafka Connect and Debezium
 *       infrastructure. Preferred for high-throughput production deployments.</li>
 * </ol>
 *
 * <p>This implementation uses the polling approach for simplicity. Migration to
 * Debezium CDC can be done transparently — the outbox table schema is compatible
 * with the Debezium Outbox Event Router.</p>
 *
 * <p>Events are published to Kafka using the aggregate ID as the partition key
 * to preserve ordering within an aggregate. After successful Kafka acknowledgement,
 * the event is marked as processed. Failed sends are left unprocessed for retry
 * on the next poll cycle.</p>
 *
 * @see OutboxEventPublisher
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private static final String FETCH_UNPROCESSED_SQL = """
            SELECT id, aggregate_type, aggregate_id, event_type, payload, metadata, created_at
            FROM outbox_events
            WHERE processed = FALSE
            ORDER BY created_at ASC
            LIMIT 100
            """;

    private static final String MARK_PROCESSED_SQL = """
            UPDATE outbox_events
            SET processed = TRUE, processed_at = ?
            WHERE id = ?::uuid
            """;

    private static final String TOPIC_PREFIX = "freightflow.booking.";

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Constructor injection — depends on JdbcTemplate for outbox reads/writes and
     * KafkaTemplate for message publishing.
     *
     * @param jdbcTemplate   the JDBC template for outbox table operations (must not be null)
     * @param kafkaTemplate  the Kafka template for message publishing (must not be null)
     */
    public OutboxProcessor(JdbcTemplate jdbcTemplate,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null");
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "KafkaTemplate must not be null");
    }

    /**
     * Polls the outbox table for unprocessed events and forwards them to Kafka.
     *
     * <p>Runs every 1 second via Spring's {@code @Scheduled}. Each invocation reads
     * up to 100 unprocessed events, publishes each to its Kafka topic, and marks
     * successfully sent events as processed. Failed events are left for the next cycle.</p>
     */
    @Scheduled(fixedDelay = 1000)
    @Profiled(value = "outboxProcessor.processOutbox", slowThresholdMs = 2000)
    @Transactional
    public void processOutbox() {
        List<Map<String, Object>> events = jdbcTemplate.queryForList(FETCH_UNPROCESSED_SQL);

        if (events.isEmpty()) {
            return;
        }

        log.debug("Processing {} unprocessed outbox event(s)", events.size());

        int successCount = 0;
        int failureCount = 0;

        for (Map<String, Object> event : events) {
            UUID eventId = (UUID) event.get("id");
            String aggregateId = event.get("aggregate_id").toString();
            String eventType = (String) event.get("event_type");
            String payload = event.get("payload").toString();

            String topic = TOPIC_PREFIX + eventType;

            try {
                kafkaTemplate.send(topic, aggregateId, payload).get();

                jdbcTemplate.update(MARK_PROCESSED_SQL, Instant.now(), eventId.toString());
                successCount++;

                log.debug("Outbox event published to Kafka: eventId={}, topic={}, aggregateId={}",
                        eventId, topic, aggregateId);

            } catch (Exception e) {
                failureCount++;
                log.error("Failed to publish outbox event to Kafka: eventId={}, topic={}, aggregateId={}. "
                        + "Event will be retried on next poll cycle.",
                        eventId, topic, aggregateId, e);
            }
        }

        if (successCount > 0 || failureCount > 0) {
            log.info("Outbox processing complete: published={}, failed={}", successCount, failureCount);
        }
    }
}
