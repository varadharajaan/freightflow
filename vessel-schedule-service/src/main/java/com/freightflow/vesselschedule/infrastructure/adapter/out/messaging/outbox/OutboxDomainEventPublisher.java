package com.freightflow.vesselschedule.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.vesselschedule.application.port.DomainEventPublisher;
import com.freightflow.vesselschedule.domain.event.VesselEvent;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Outbox-backed domain event publisher for vessel events.
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDomainEventPublisher.class);

    private static final String AGGREGATE_TYPE = "Voyage";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(OutboxEventRepository outboxEventRepository,
                                      ObjectMapper objectMapper) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    @Transactional
    @Profiled(value = "outboxPublisher.publishVesselEvent", slowThresholdMs = 100)
    public void publish(VesselEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String metadata = objectMapper.writeValueAsString(buildMetadata(event));

            OutboxEvent outboxEvent = OutboxEvent.pending(
                    event.eventId(),
                    AGGREGATE_TYPE,
                    event.voyageId(),
                    event.eventType(),
                    payload,
                    metadata
            );

            outboxEventRepository.save(outboxEvent);
            log.debug("Vessel event written to outbox: eventId={}, type={}",
                    event.eventId(), event.eventType());
        } catch (DataIntegrityViolationException ex) {
            log.info("Skipping duplicate outbox event write: eventId={}", event.eventId());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize vessel event " + event.eventId(), ex);
        }
    }

    private Map<String, Object> buildMetadata(VesselEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventId", event.eventId().toString());
        metadata.put("occurredAt", event.occurredAt().toString());
        metadata.put("recordedAt", Instant.now().toString());

        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            metadata.put("correlationId", correlationId);
        }
        return metadata;
    }
}
