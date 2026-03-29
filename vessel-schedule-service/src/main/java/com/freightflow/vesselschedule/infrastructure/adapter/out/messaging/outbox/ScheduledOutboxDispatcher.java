package com.freightflow.vesselschedule.infrastructure.adapter.out.messaging.outbox;

import com.freightflow.vesselschedule.application.port.OutboxDispatcher;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Polling outbox dispatcher that forwards pending events to Kafka.
 */
@Component
public class ScheduledOutboxDispatcher implements OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledOutboxDispatcher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public ScheduledOutboxDispatcher(OutboxEventRepository outboxEventRepository,
                                     KafkaTemplate<String, String> kafkaTemplate,
                                     @Value("${freightflow.outbox.vessel-topic:vessel.events}") String topic) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
        this.topic = Objects.requireNonNull(topic);
    }

    @Override
    @Scheduled(fixedDelayString = "${freightflow.outbox.dispatch-interval-ms:1000}")
    @Transactional
    @Profiled(value = "vesselOutboxDispatcher.dispatch", slowThresholdMs = 2000)
    public void dispatchPending() {
        List<OutboxEvent> pending = outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
                event.markPublished();
            } catch (Exception ex) {
                event.markFailed(ex.getMessage());
                if (event.getFailureCount() < 5) {
                    event.resetToPending();
                }
                log.error("Failed to dispatch outbox event: eventId={}, type={}, failureCount={}",
                        event.getEventId(), event.getEventType(), event.getFailureCount(), ex);
            }
        }
    }
}
