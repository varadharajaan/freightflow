package com.freightflow.booking.infrastructure.adapter.out.messaging;

import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.booking.domain.port.BookingEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Infrastructure adapter that publishes domain events via Spring's event system.
 *
 * <p>This is the initial implementation of the {@link BookingEventPublisher} outbound port.
 * It uses Spring's in-process {@link ApplicationEventPublisher} for synchronous event
 * delivery within the same JVM. This is suitable for development and early stages;
 * a Kafka-based implementation will replace this for production cross-service communication.</p>
 *
 * <p>Follows the Dependency Inversion Principle: the domain defines the port interface,
 * this infrastructure class implements it.</p>
 *
 * @see BookingEventPublisher
 * @see ApplicationEventPublisher
 */
@Component
public class SpringEventBookingPublisher implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringEventBookingPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Creates a new {@code SpringEventBookingPublisher}.
     *
     * @param applicationEventPublisher Spring's application event publisher (must not be null)
     */
    public SpringEventBookingPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher,
                "ApplicationEventPublisher must not be null");
    }

    /**
     * Publishes a booking domain event via Spring's application event system.
     *
     * <p>Each event is logged at INFO level with its type and associated booking ID
     * for observability and debugging.</p>
     *
     * @param event the domain event to publish
     */
    @Override
    public void publish(BookingEvent event) {
        log.info("Publishing domain event: type={}, bookingId={}, eventId={}",
                event.eventType(), event.bookingId().asString(), event.eventId());

        applicationEventPublisher.publishEvent(event);

        log.debug("Domain event published successfully: type={}, bookingId={}",
                event.eventType(), event.bookingId().asString());
    }
}
