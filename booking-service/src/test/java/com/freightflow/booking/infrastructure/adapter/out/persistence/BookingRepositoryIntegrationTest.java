package com.freightflow.booking.infrastructure.adapter.out.persistence;

import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.freightflow.booking.infrastructure.adapter.out.persistence.repository.SpringDataBookingRepository;
import com.freightflow.commons.testing.ContainerizedDependencies;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
@Testcontainers
class BookingRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            ContainerizedDependencies.postgres("freightflow_booking_test", "test", "test");

    @Container
    static final KafkaContainer KAFKA = ContainerizedDependencies.kafka();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ContainerizedDependencies.registerPostgres(registry, POSTGRES);
        ContainerizedDependencies.registerKafka(registry, KAFKA);
    }

    @Autowired
    private SpringDataBookingRepository repository;

    @Test
    void shouldPersistAndLoadBookingUsingPostgresAndKafkaEnabledContext() {
        BookingJpaEntity entity = BookingJpaEntity.createForMapping();
        entity.setId(UUID.randomUUID());
        entity.setCustomerId(UUID.randomUUID());
        entity.setStatus(com.freightflow.booking.domain.model.BookingStatus.DRAFT);
        entity.setCommodityCode("GEN");
        entity.setDescription("General Cargo");
        entity.setWeightValue(BigDecimal.valueOf(1500));
        entity.setWeightUnit("KG");
        entity.setContainerType("TWENTY_FOOT");
        entity.setContainerCount(2);
        entity.setOriginPort("CNSHA");
        entity.setDestinationPort("DEHAM");
        entity.setRequestedDepartureDate(LocalDate.now().plusDays(14));
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setVersion(0L);

        repository.save(entity);

        assertThat(repository.findById(entity.getId()))
                .isPresent()
                .get()
                .extracting(BookingJpaEntity::getStatus, BookingJpaEntity::getOriginPort, BookingJpaEntity::getDestinationPort)
                .containsExactly("DRAFT", "CNSHA", "DEHAM");
    }
}
