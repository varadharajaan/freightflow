package com.freightflow.trackingservice.infrastructure.adapter.out.persistence;

import com.freightflow.trackingservice.infrastructure.adapter.out.persistence.entity.ContainerJpaEntity;
import com.freightflow.trackingservice.infrastructure.adapter.out.persistence.repository.SpringDataContainerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.enabled=false")
class ContainerRepositoryIntegrationTest {

    @Autowired
    private SpringDataContainerRepository containerRepository;

    @Test
    void shouldPersistAndQueryContainersByBooking() {
        UUID bookingId = UUID.randomUUID();

        ContainerJpaEntity entity = new ContainerJpaEntity();
        entity.setContainerId("MSCU1234567");
        entity.setBookingId(bookingId);
        entity.setStatus("EMPTY");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setVersion(0);

        containerRepository.saveAndFlush(entity);

        List<ContainerJpaEntity> results = containerRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getContainerId()).isEqualTo("MSCU1234567");
    }
}
