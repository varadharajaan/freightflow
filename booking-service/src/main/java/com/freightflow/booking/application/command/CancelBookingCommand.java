package com.freightflow.booking.application.command;

/**
 * Command to cancel a booking with a reason.
 *
 * @param bookingId the booking to cancel
 * @param reason    the cancellation reason
 */
public record CancelBookingCommand(
        String bookingId,
        String reason
) implements BookingCommand {}
