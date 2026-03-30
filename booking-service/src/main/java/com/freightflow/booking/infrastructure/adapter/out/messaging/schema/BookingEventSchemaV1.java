package com.freightflow.booking.infrastructure.adapter.out.messaging.schema;

import com.freightflow.booking.domain.event.BookingCreated;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Version 1 serialization schema for {@link BookingCreated} domain events.
 *
 * <p>This record defines the wire format (JSON) for the initial version of the BookingCreated
 * event. All field types are primitives or standard JDK types to ensure clean serialization
 * without custom Jackson modules for domain value objects.</p>
 *
 * <p>Schema evolution strategy: new versions add fields with defaults. Consumers reading V1
 * events receive {@code null} for fields added in later versions. The {@link EventSchemaRegistry}
 * handles automatic upcasting from V1 to V2 when needed.</p>
 *
 * @param eventId                unique event identifier
 * @param bookingId              the booking aggregate ID (UUID string)
 * @param customerId             the customer who placed the booking (UUID string)
 * @param origin                 origin port code (UN/LOCODE)
 * @param destination            destination port code (UN/LOCODE)
 * @param containerType          type of container requested (enum name)
 * @param containerCount         number of containers
 * @param requestedDepartureDate desired departure date
 * @param occurredAt             when the event occurred
 * @see BookingEventSchemaV2
 * @see EventSchemaRegistry
 */
public record BookingEventSchemaV1(
        UUID eventId,
        String bookingId,
        String customerId,
        String origin,
        String destination,
        String containerType,
        int containerCount,
        LocalDate requestedDepartureDate,
        Instant occurredAt
) {

    /**
     * Factory method to create a V1 schema instance from a {@link BookingCreated} domain event.
     *
     * <p>Extracts primitive values from domain value objects to produce a serialization-friendly
     * representation suitable for JSON encoding.</p>
     *
     * @param event the domain event to convert
     * @return a V1 schema record ready for serialization
     */
    public static BookingEventSchemaV1 from(BookingCreated event) {
        return new BookingEventSchemaV1(
                event.eventId(),
                event.bookingId().asString(),
                event.customerId().asString(),
                event.origin().value(),
                event.destination().value(),
                event.containerType().name(),
                event.containerCount(),
                event.requestedDepartureDate(),
                event.occurredAt()
        );
    }
}
