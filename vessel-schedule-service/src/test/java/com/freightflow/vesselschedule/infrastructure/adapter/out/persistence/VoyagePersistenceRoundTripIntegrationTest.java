package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence;

import com.freightflow.vesselschedule.domain.model.PortCall;
import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.VoyageJpaEntity;
import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.repository.SpringDataVoyageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@Import(VoyageEntityMapper.class)
class VoyagePersistenceRoundTripIntegrationTest {

    @Autowired
    private SpringDataVoyageRepository voyageRepository;

    @Autowired
    private VoyageEntityMapper mapper;

    @Test
    void shouldPersistAndReconstituteVoyageWithPortCalls() {
        Voyage voyage = Voyage.create(
                UUID.randomUUID(),
                "VYG-PERSIST-001",
                300,
                List.of(
                        new PortCall(
                                "CNSHA",
                                Instant.parse("2026-02-01T00:00:00Z"),
                                Instant.parse("2026-02-01T12:00:00Z"),
                                Instant.parse("2026-02-01T01:30:00Z"),
                                Instant.parse("2026-02-01T13:15:00Z")
                        ),
                        new PortCall(
                                "USLAX",
                                Instant.parse("2026-02-10T00:00:00Z"),
                                Instant.parse("2026-02-10T12:00:00Z"),
                                null,
                                null
                        )
                )
        );

        VoyageJpaEntity toPersist = mapper.toEntity(voyage);
        voyageRepository.saveAndFlush(toPersist);

        VoyageJpaEntity persisted = voyageRepository.findById(voyage.getVoyageId()).orElseThrow();
        Voyage reconstituted = mapper.toDomain(persisted);

        assertThat(reconstituted.getVoyageId()).isEqualTo(voyage.getVoyageId());
        assertThat(reconstituted.getRoute()).hasSize(2);
        assertThat(reconstituted.getRoute().get(0).port()).isEqualTo("CNSHA");
        assertThat(reconstituted.getRoute().get(0).actualArrival()).isEqualTo(Instant.parse("2026-02-01T01:30:00Z"));
        assertThat(reconstituted.getRoute().get(0).actualDeparture()).isEqualTo(Instant.parse("2026-02-01T13:15:00Z"));
        assertThat(reconstituted.getRoute().get(1).port()).isEqualTo("USLAX");
        assertThat(reconstituted.getRoute().get(1).actualArrival()).isNull();
        assertThat(reconstituted.getRoute().get(1).actualDeparture()).isNull();
    }
}
