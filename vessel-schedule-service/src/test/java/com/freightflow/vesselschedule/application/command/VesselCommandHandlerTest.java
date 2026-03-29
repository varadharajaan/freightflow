package com.freightflow.vesselschedule.application.command;

import com.freightflow.vesselschedule.application.port.CacheInvalidationService;
import com.freightflow.vesselschedule.application.port.DomainEventPublisher;
import com.freightflow.vesselschedule.domain.model.PortCall;
import com.freightflow.vesselschedule.domain.model.Voyage;
import com.freightflow.vesselschedule.domain.port.VoyageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VesselCommandHandlerTest {

    @Mock
    private VoyageRepository voyageRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Test
    void reserveCapacityShouldPublishEventsAndInvalidateCaches() {
        Voyage voyage = Voyage.create(
                UUID.randomUUID(),
                "VYG-CMD-001",
                200,
                List.of(
                        PortCall.planned("CNSHA", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T12:00:00Z")),
                        PortCall.planned("USLAX", Instant.parse("2026-01-10T00:00:00Z"), Instant.parse("2026-01-10T12:00:00Z"))
                )
        );

        VesselCommandHandler handler = new VesselCommandHandler(
                voyageRepository,
                domainEventPublisher,
                cacheInvalidationService
        );

        when(voyageRepository.findById(voyage.getVoyageId())).thenReturn(Optional.of(voyage));
        when(voyageRepository.save(any(Voyage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.reserveCapacity(voyage.getVoyageId(), UUID.randomUUID(), 10);

        verify(domainEventPublisher).publishAll(any());
        verify(cacheInvalidationService).invalidateVoyage(voyage.getVoyageId());
        verify(cacheInvalidationService).invalidateVoyagesByVessel(voyage.getVesselId());
        verify(cacheInvalidationService).invalidateAvailableRoutes();
    }
}
