package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence;

import com.freightflow.vesselschedule.domain.model.PortCall;
import com.freightflow.vesselschedule.domain.model.Voyage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VoyageEntityMapperRoundTripTest {

    private final VoyageEntityMapper mapper = new VoyageEntityMapper();

    @Test
    void shouldPreservePortCallFieldsInRoundTripMapping() {
        Voyage original = Voyage.create(
                UUID.randomUUID(),
                "VYG-RT-001",
                400,
                List.of(
                        new PortCall(
                                "CNSHA",
                                Instant.parse("2026-01-01T00:00:00Z"),
                                Instant.parse("2026-01-01T12:00:00Z"),
                                Instant.parse("2026-01-01T01:00:00Z"),
                                Instant.parse("2026-01-01T13:00:00Z")
                        ),
                        new PortCall(
                                "USLAX",
                                Instant.parse("2026-01-10T00:00:00Z"),
                                Instant.parse("2026-01-10T12:00:00Z"),
                                null,
                                null
                        )
                )
        );

        Voyage roundTrip = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.getRoute().size(), roundTrip.getRoute().size());
        assertEquals(original.getRoute().get(0).port(), roundTrip.getRoute().get(0).port());
        assertEquals(original.getRoute().get(0).estimatedArrival(), roundTrip.getRoute().get(0).estimatedArrival());
        assertEquals(original.getRoute().get(0).actualArrival(), roundTrip.getRoute().get(0).actualArrival());
        assertNotNull(roundTrip.getRoute().get(0).actualDeparture());
    }
}
