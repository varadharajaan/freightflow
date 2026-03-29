package com.freightflow.vesselschedule.infrastructure.adapter.out.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledOutboxDispatcherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldMarkEventPublishedWhenKafkaSendSucceeds() {
        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(), "Voyage", UUID.randomUUID(), "VoyageArrived", "{}", "{}");

        ScheduledOutboxDispatcher dispatcher = new ScheduledOutboxDispatcher(
                outboxEventRepository,
                kafkaTemplate,
                "vessel.events"
        );

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatchPending();

        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
    }

    @Test
    void shouldRetainPendingStatusAndIncrementFailureCountWhenKafkaSendFails() {
        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(), "Voyage", UUID.randomUUID(), "VoyageArrived", "{}", "{}");

        ScheduledOutboxDispatcher dispatcher = new ScheduledOutboxDispatcher(
                outboxEventRepository,
                kafkaTemplate,
                "vessel.events"
        );

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        dispatcher.dispatchPending();

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(1, event.getFailureCount());
    }
}
