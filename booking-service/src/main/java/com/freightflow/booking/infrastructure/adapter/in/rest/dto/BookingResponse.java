package com.freightflow.booking.infrastructure.adapter.in.rest.dto;

import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.BookingStatus;
import com.freightflow.booking.domain.model.ContainerType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Outbound REST DTO representing a booking in API responses.
 *
 * <p>This record maps from the {@link Booking} domain entity to a flat JSON-serializable
 * structure. It intentionally hides internal domain details (e.g., cargo value object,
 * domain events) from API consumers, following the Interface Segregation Principle.</p>
 *
 * @param bookingId              unique booking identifier
 * @param customerId             UUID of the owning customer
 * @param status                 current booking lifecycle status
 * @param origin                 origin port code (UN/LOCODE)
 * @param destination            destination port code (UN/LOCODE)
 * @param containerType          type of container booked
 * @param containerCount         number of containers
 * @param voyageId               assigned voyage ID (null if not yet confirmed)
 * @param requestedDepartureDate desired departure date
 * @param createdAt              when the booking was created
 * @param updatedAt              when the booking was last modified
 */
public record BookingResponse(
        String bookingId,
        String customerId,
        BookingStatus status,
        String origin,
        String destination,
        ContainerType containerType,
        int containerCount,
        String voyageId,
        LocalDate requestedDepartureDate,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Factory method that maps a domain {@link Booking} to an API response DTO.
     *
     * <p>Extracts nested value objects (Cargo, PortCode, etc.) into flat fields
     * suitable for JSON serialization.</p>
     *
     * @param booking the domain booking aggregate
     * @return a new {@code BookingResponse} DTO
     */
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId().asString(),
                booking.getCustomerId().asString(),
                booking.getStatus(),
                booking.getCargo().origin().value(),
                booking.getCargo().destination().value(),
                booking.getCargo().containerType(),
                booking.getCargo().containerCount(),
                booking.getVoyageId().map(v -> v.asString()).orElse(null),
                booking.getRequestedDepartureDate(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}
