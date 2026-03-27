package com.freightflow.booking.infrastructure.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for the booking service.
 *
 * <p>Defines all Kafka topics used by the booking domain. Topics are auto-created
 * on startup if they don't exist (requires {@code auto.create.topics.enable=true}
 * on the Kafka broker or AdminClient permissions).</p>
 *
 * <h3>Topic Design Decisions (see ADR-003)</h3>
 * <ul>
 *   <li><b>Partitioning by bookingId</b> — ensures ordering per booking aggregate</li>
 *   <li><b>12 partitions</b> — allows up to 12 consumer instances for parallel processing</li>
 *   <li><b>Replication factor 3</b> — survives 2 broker failures (production)</li>
 *   <li><b>Retention 30 days</b> — sufficient for replay and debugging</li>
 *   <li><b>Dead letter topic</b> — failed messages routed here for manual investigation</li>
 * </ul>
 *
 * @see com.freightflow.booking.infrastructure.adapter.out.messaging.kafka.KafkaBookingEventPublisher
 */
@Configuration
public class KafkaTopicConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicConfig.class);

    public static final String BOOKING_EVENTS_TOPIC = "booking.events";
    public static final String BOOKING_COMMANDS_TOPIC = "booking.commands";
    public static final String BOOKING_DLQ_TOPIC = "booking.events.dlq";

    @Value("${freightflow.kafka.partitions:12}")
    private int partitions;

    @Value("${freightflow.kafka.replication-factor:1}")
    private int replicationFactor;

    /**
     * Main booking events topic — carries all domain events (BookingCreated, etc.).
     * Partitioned by bookingId for per-aggregate ordering.
     */
    @Bean
    public NewTopic bookingEventsTopic() {
        log.info("Configuring topic: name={}, partitions={}, replication={}",
                BOOKING_EVENTS_TOPIC, partitions, replicationFactor);

        return TopicBuilder.name(BOOKING_EVENTS_TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", String.valueOf(30L * 24 * 60 * 60 * 1000)) // 30 days
                .config("cleanup.policy", "delete")
                .config("min.insync.replicas", "1")
                .build();
    }

    /**
     * Booking commands topic — for async command processing (future use).
     */
    @Bean
    public NewTopic bookingCommandsTopic() {
        return TopicBuilder.name(BOOKING_COMMANDS_TOPIC)
                .partitions(6)
                .replicas(replicationFactor)
                .config("retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000)) // 7 days
                .build();
    }

    /**
     * Dead letter queue topic — receives messages that failed processing after retries.
     * Retention is 90 days for investigation and manual replay.
     */
    @Bean
    public NewTopic bookingDlqTopic() {
        log.info("Configuring DLQ topic: name={}", BOOKING_DLQ_TOPIC);

        return TopicBuilder.name(BOOKING_DLQ_TOPIC)
                .partitions(3)
                .replicas(replicationFactor)
                .config("retention.ms", String.valueOf(90L * 24 * 60 * 60 * 1000)) // 90 days
                .build();
    }
}
