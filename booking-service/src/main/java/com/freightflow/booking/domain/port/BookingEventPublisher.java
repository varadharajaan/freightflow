package com.freightflow.booking.domain.port;

import com.freightflow.booking.domain.event.BookingEvent;

import java.util.List;

/**
 * Outbound port for publishing domain events.
 *
 * <p>Abstracts the event publishing mechanism from the domain layer.
 * The infrastructure layer provides the implementation — initially via
 * Spring's {@code ApplicationEventPublisher}, later via Kafka producer.</p>
 *
 * <p>This follows the Dependency Inversion Principle: the domain defines
 * the contract, the infrastructure implements it.</p>
 */
public interface BookingEventPublisher {

    /**
     * Publishes a single domain event.
     *
     * @param event the domain event to publish
     */
    void publish(BookingEvent event);

    /**
     * Publishes a batch of domain events in order.
     *
     * <p>Used after persisting an aggregate to publish all collected events.
     * Events are published in the order they were registered.</p>
     *
     * @param events the domain events to publish
     */
    default void publishAll(List<BookingEvent> events) {
        events.forEach(this::publish);
    }
}
