package com.freightflow.booking.infrastructure.adapter.out.messaging.schema;

import com.freightflow.booking.domain.event.BookingCreated;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Version 2 serialization schema for {@link BookingCreated} domain events.
 *
 * <p>Extends V1 with two additional fields — {@code weight} and {@code commodityCode} — as
 * a backward-compatible addition. These fields are nullable so that V1 events can be upcasted
 * to V2 without data loss (they simply carry {@code null} for the new fields).</p>
 *
 * <h3>Compatibility Guarantees</h3>
 * <ul>
 *   <li><b>Backward compatible</b>: V2 consumers can read V1 events (new fields default to null)</li>
 *   <li><b>Forward compatible</b>: V1 consumers can read V2 events (unknown fields are ignored)</li>
 * </ul>
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
 * @param weight                 total cargo weight in kilograms (nullable — absent in V1 events)
 * @param commodityCode          HS commodity code for the cargo (nullable — absent in V1 events)
 * @see BookingEventSchemaV1
 * @see EventSchemaRegistry
 */
public record BookingEventSchemaV2(
        UUID eventId,
        String bookingId,
        String customerId,
        String origin,
        String destination,
        String containerType,
        int containerCount,
        LocalDate requestedDepartureDate,
        Instant occurredAt,
        Double weight,
        String commodityCode
) {

    /**
     * Factory method to create a V2 schema instance from a {@link BookingCreated} domain event.
     *
     * <p>Extracts primitive values from domain value objects. The {@code weight} and
     * {@code commodityCode} fields are set to {@code null} since the current {@link BookingCreated}
     * event does not carry these fields — they will be populated when the domain model evolves.</p>
     *
     * @param event the domain event to convert
     * @return a V2 schema record ready for serialization
     */
    public static BookingEventSchemaV2 from(BookingCreated event) {
        return new BookingEventSchemaV2(
                event.eventId(),
                event.bookingId().asString(),
                event.customerId().asString(),
                event.origin().value(),
                event.destination().value(),
                event.containerType().name(),
                event.containerCount(),
                event.requestedDepartureDate(),
                event.occurredAt(),
                null,
                null
        );
    }

    /**
     * Upcasts a V1 schema instance to V2, filling new fields with defaults.
     *
     * <p>This method enables backward compatibility — when a consumer expecting V2 receives
     * a V1 event, it can be transparently upgraded. The new fields ({@code weight} and
     * {@code commodityCode}) are set to {@code null}, indicating they were not present in
     * the original event.</p>
     *
     * @param v1 the V1 schema record to upcast
     * @return a V2 schema record with new fields set to null
     */
    public static BookingEventSchemaV2 fromV1(BookingEventSchemaV1 v1) {
        return new BookingEventSchemaV2(
                v1.eventId(),
                v1.bookingId(),
                v1.customerId(),
                v1.origin(),
                v1.destination(),
                v1.containerType(),
                v1.containerCount(),
                v1.requestedDepartureDate(),
                v1.occurredAt(),
                null,
                null
        );
    }
}
