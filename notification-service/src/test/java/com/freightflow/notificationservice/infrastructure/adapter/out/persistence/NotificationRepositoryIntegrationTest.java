package com.freightflow.notificationservice.infrastructure.adapter.out.persistence;

import com.freightflow.notificationservice.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import com.freightflow.notificationservice.infrastructure.adapter.out.persistence.repository.SpringDataNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.enabled=false")
class NotificationRepositoryIntegrationTest {

    @Autowired
    private SpringDataNotificationRepository notificationRepository;

    @Test
    void shouldPersistAndQueryNotificationsByRecipient() {
        UUID recipientId = UUID.randomUUID();

        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setRecipientId(recipientId);
        entity.setChannelType("EmailChannel");
        entity.setChannelData("ops@acme.test");
        entity.setSubject("Booking update");
        entity.setBody("Container loaded");
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setVersion(0);

        notificationRepository.saveAndFlush(entity);

        List<NotificationJpaEntity> results = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSubject()).isEqualTo("Booking update");
    }
}
