package com.freightflow.vesselschedule.infrastructure.adapter.out.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = "spring.flyway.enabled=false")
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository repository;

    @Test
    void shouldEnforceUniqueEventIdForIdempotency() {
        UUID sharedEventId = UUID.randomUUID();

        OutboxEvent first = OutboxEvent.pending(
                sharedEventId, "Voyage", UUID.randomUUID(), "VoyageDeparted", "{}", "{}");
        OutboxEvent second = OutboxEvent.pending(
                sharedEventId, "Voyage", UUID.randomUUID(), "VoyageDeparted", "{}", "{}");

        repository.saveAndFlush(first);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(second));
    }
}
