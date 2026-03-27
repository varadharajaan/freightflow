package com.freightflow.booking.infrastructure.config.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka consumer configuration for the booking service.
 *
 * <p>Creates a {@link ConcurrentKafkaListenerContainerFactory} with settings optimized
 * for reliable, at-least-once event consumption with dead-letter queue support.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>group.id = booking-service-group</b> — all instances of this service share
 *       the same consumer group. Kafka assigns partitions across instances for parallel
 *       processing. If one instance dies, its partitions are rebalanced to survivors.</li>
 *   <li><b>auto.offset.reset = earliest</b> — on first join (or after offset expiry),
 *       the consumer starts from the beginning of the topic. This ensures no events are
 *       missed on initial deployment or after a long outage.</li>
 *   <li><b>enable.auto.commit = false</b> — offsets are committed manually after
 *       successful processing via {@code Acknowledgment.acknowledge()}. This prevents
 *       committing offsets for messages that failed processing (at-least-once guarantee).</li>
 *   <li><b>max.poll.records = 100</b> — limits records per poll to avoid long processing
 *       cycles that could trigger a consumer group rebalance (max.poll.interval.ms).</li>
 *   <li><b>Dead Letter Queue</b> — after 3 retries with 2-second fixed backoff, failed
 *       messages are published to the DLQ topic ({@code booking.events.dlq}) for manual
 *       investigation. This prevents poison-pill messages from blocking the consumer.</li>
 *   <li><b>MANUAL ack mode</b> — the container delegates offset commits to the listener
 *       via the {@code Acknowledgment} parameter, giving us fine-grained control.</li>
 * </ul>
 *
 * @see KafkaTopicConfig
 * @see com.freightflow.booking.infrastructure.adapter.in.messaging.BookingEventKafkaConsumer
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private static final String CONSUMER_GROUP_ID = "booking-service-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 2000L;
    private static final int MAX_POLL_RECORDS = 100;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the Kafka {@link ConsumerFactory} with at-least-once delivery settings.
     *
     * <p>The factory produces consumer instances configured for manual offset management
     * and the {@code booking-service-group} consumer group. All deserialization uses
     * {@link StringDeserializer} — JSON parsing is handled by the listener adapter.</p>
     *
     * @return a configured {@link ConsumerFactory} for String key/value pairs
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        log.debug("Initializing Kafka ConsumerFactory with bootstrapServers={}", bootstrapServers);

        Map<String, Object> configProps = new HashMap<>();

        // --- Connection ---
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // --- Deserialization ---
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // --- Consumer Group ---
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP_ID);

        // --- Offset management: start from earliest, no auto-commit ---
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // --- Poll tuning: limit records per poll to avoid rebalance timeouts ---
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLL_RECORDS);

        log.info("Kafka ConsumerFactory configured: bootstrapServers={}, groupId={}, autoOffsetReset=earliest, autoCommit=false, maxPollRecords={}",
                bootstrapServers, CONSUMER_GROUP_ID, MAX_POLL_RECORDS);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Creates the {@link ConcurrentKafkaListenerContainerFactory} used by {@code @KafkaListener} methods.
     *
     * <p>Configures manual acknowledgment mode and a {@link DefaultErrorHandler} with
     * dead-letter publishing. When a message fails processing after {@value #MAX_RETRY_ATTEMPTS}
     * retries (with {@value #RETRY_BACKOFF_MS}ms backoff), it is forwarded to the DLQ topic
     * for offline investigation.</p>
     *
     * @param consumerFactory the configured consumer factory
     * @param kafkaTemplate   the Kafka template used by the DLQ recoverer to publish failed messages
     * @return a configured listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        Objects.requireNonNull(consumerFactory, "ConsumerFactory must not be null");
        Objects.requireNonNull(kafkaTemplate, "KafkaTemplate must not be null");

        log.debug("Initializing ConcurrentKafkaListenerContainerFactory");

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // --- Manual acknowledgment: offsets committed only after successful processing ---
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // --- Dead Letter Queue: publish failed messages after retry exhaustion ---
        DeadLetterPublishingRecoverer dlqRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    log.error("Message exhausted retries — routing to DLQ: topic={}, partition={}, offset={}, error={}",
                            record.topic(), record.partition(), record.offset(), exception.getMessage());
                    return new org.apache.kafka.common.TopicPartition(
                            KafkaTopicConfig.BOOKING_DLQ_TOPIC, record.partition());
                });

        // --- Error handling: 3 retries with 2-second fixed backoff, then DLQ ---
        FixedBackOff fixedBackOff = new FixedBackOff(RETRY_BACKOFF_MS, MAX_RETRY_ATTEMPTS);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(dlqRecoverer, fixedBackOff);

        factory.setCommonErrorHandler(errorHandler);

        log.info("KafkaListenerContainerFactory configured: ackMode=MANUAL, retries={}, backoffMs={}, dlqTopic={}",
                MAX_RETRY_ATTEMPTS, RETRY_BACKOFF_MS, KafkaTopicConfig.BOOKING_DLQ_TOPIC);

        return factory;
    }
}
