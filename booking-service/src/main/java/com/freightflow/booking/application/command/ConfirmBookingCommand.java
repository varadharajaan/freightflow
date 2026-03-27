package com.freightflow.booking.application.command;

/**
 * Command to confirm a booking and assign it to a voyage.
 *
 * @param bookingId the booking to confirm
 * @param voyageId  the voyage to assign
 */
public record ConfirmBookingCommand(
        String bookingId,
        String voyageId
) implements BookingCommand {}
