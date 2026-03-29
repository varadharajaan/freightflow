package com.freightflow.vesselschedule.domain.model;

import com.freightflow.commons.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoyageTest {

    @Test
    void reserveCapacityShouldReduceRemainingCapacity() {
        Voyage voyage = Voyage.create(
                UUID.randomUUID(),
                "VYG-001",
                100,
                List.of(
                        PortCall.planned("CNSHA", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T12:00:00Z")),
                        PortCall.planned("USLAX", Instant.parse("2026-01-10T00:00:00Z"), Instant.parse("2026-01-10T12:00:00Z"))
                )
        );

        voyage.reserveCapacity(UUID.randomUUID(), 25);

        assertEquals(75, voyage.getRemainingCapacityTeu());
    }

    @Test
    void reserveCapacityShouldFailWhenVoyageAlreadyDeparted() {
        Voyage voyage = Voyage.create(
                UUID.randomUUID(),
                "VYG-002",
                100,
                List.of(
                        PortCall.planned("CNSHA", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T12:00:00Z")),
                        PortCall.planned("USLAX", Instant.parse("2026-01-10T00:00:00Z"), Instant.parse("2026-01-10T12:00:00Z"))
                )
        );
        voyage.depart();

        assertThrows(ConflictException.class, () -> voyage.reserveCapacity(UUID.randomUUID(), 1));
    }
}
