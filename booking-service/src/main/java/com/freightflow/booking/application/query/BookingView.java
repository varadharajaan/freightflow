package com.freightflow.booking.application.query;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Read model (projection) for a booking — the query response in CQRS.
 *
 * <p>This record is the <b>flat, denormalized</b> representation optimized for reads.
 * It is materialized from domain events by the {@link BookingProjectionUpdater} and
 * served by the {@link BookingQueryHandler}. Unlike the write-side aggregate, this
 * projection has no domain logic and no invariants — it is pure data.</p>
 *
 * <h3>CQRS Design</h3>
 * <ul>
 *   <li><b>Write side</b>: {@code Booking} aggregate + {@code BookingCommandHandler}</li>
 *   <li><b>Read side</b>: {@code BookingView} projection + {@code BookingQueryHandler}</li>
 * </ul>
 *
 * @param bookingId              the booking aggregate ID
 * @param customerId             the customer who placed the booking
 * @param status                 the current booking status (DRAFT, CONFIRMED, CANCELLED)
 * @param originPort             origin port code
 * @param destinationPort        destination port code
 * @param containerType          type of container requested
 * @param containerCount         number of containers
 * @param voyageId               the assigned voyage (nullable — only set after confirmation)
 * @param cancellationReason     the cancellation reason (nullable — only set after cancellation)
 * @param requestedDepartureDate desired departure date
 * @param createdAt              when the booking was created
 * @param updatedAt              when the projection was last updated
 */
public record BookingView(
        String bookingId,
        String customerId,
        String status,
        String originPort,
        String destinationPort,
        String containerType,
        int containerCount,
        String voyageId,
        String cancellationReason,
        LocalDate requestedDepartureDate,
        Instant createdAt,
        Instant updatedAt
) {
}
