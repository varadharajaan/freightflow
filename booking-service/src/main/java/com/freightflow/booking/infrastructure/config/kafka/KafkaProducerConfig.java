package com.freightflow.booking.infrastructure.config.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for the booking service.
 *
 * <p>Creates a {@link KafkaTemplate} with a {@link ProducerFactory} tuned for
 * durability and exactly-once semantics while maintaining reasonable throughput.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>acks=all</b> — waits for all in-sync replicas to acknowledge, preventing
 *       data loss even if a broker dies mid-write. Required for exactly-once.</li>
 *   <li><b>enable.idempotence=true</b> — the broker deduplicates messages using a
 *       producer ID + sequence number, giving us exactly-once delivery without
 *       transactional overhead.</li>
 *   <li><b>retries=3</b> — retries transient failures (leader election, network blips).
 *       Combined with idempotence, retries are safe and won't create duplicates.</li>
 *   <li><b>retry.backoff.ms=1000</b> — avoids hammering the broker during transient
 *       failures, giving the cluster time to recover.</li>
 *   <li><b>linger.ms=5</b> — waits up to 5ms to batch messages together, improving
 *       throughput by ~2-5x with negligible latency impact for event-driven flows.</li>
 *   <li><b>batch.size=16384</b> — default 16KB batch buffer; works well with our
 *       JSON-serialized booking events (typically 1-3KB each).</li>
 *   <li><b>StringSerializer</b> — keys and values are strings; JSON serialization
 *       is handled by the publisher adapter via Jackson ObjectMapper.</li>
 * </ul>
 *
 * @see KafkaTopicConfig
 * @see com.freightflow.booking.infrastructure.adapter.out.messaging.kafka.KafkaBookingEventPublisher
 */
@Configuration
public class KafkaProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the Kafka {@link ProducerFactory} with durability and throughput settings.
     *
     * <p>The factory creates producer instances that are thread-safe and can be shared
     * across the application. Configuration is externalized via {@code application.yml}
     * for the bootstrap servers; all other settings are hardcoded here to enforce
     * consistency across environments.</p>
     *
     * @return a configured {@link ProducerFactory} for String key/value pairs
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        log.debug("Initializing Kafka ProducerFactory with bootstrapServers={}", bootstrapServers);

        Map<String, Object> configProps = new HashMap<>();

        // --- Connection ---
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // --- Serialization ---
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // --- Durability: acks=all ensures all ISR replicas acknowledge the write ---
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        // --- Exactly-once: broker deduplicates using producer ID + sequence number ---
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // --- Retry policy: handles transient broker failures gracefully ---
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        // --- Throughput: micro-batching for better network utilization ---
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        log.info("Kafka ProducerFactory configured: bootstrapServers={}, acks=all, idempotence=true, retries=3, linger.ms=5",
                bootstrapServers);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Creates the {@link KafkaTemplate} used by outbound adapters to publish messages.
     *
     * <p>The template wraps the producer factory and provides a high-level API for
     * sending records. It is injected into
     * {@link com.freightflow.booking.infrastructure.adapter.out.messaging.kafka.KafkaBookingEventPublisher}.</p>
     *
     * @param producerFactory the configured producer factory
     * @return a {@link KafkaTemplate} for String key/value pairs
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        log.info("Creating KafkaTemplate with configured ProducerFactory");
        return new KafkaTemplate<>(producerFactory);
    }
}
