package com.freightflow.vesselschedule.application.port;

import com.freightflow.vesselschedule.domain.event.VesselEvent;

import java.util.List;

/**
 * Outbound application port for publishing vessel domain events.
 */
public interface DomainEventPublisher {

    void publish(VesselEvent event);

    default void publishAll(List<VesselEvent> events) {
        events.forEach(this::publish);
    }
}
