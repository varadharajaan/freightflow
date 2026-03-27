package com.freightflow.booking.application.command;

import com.freightflow.booking.domain.model.ContainerType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sealed interface for all booking commands.
 *
 * <p>Commands represent intentions to change state (write side of CQRS).
 * Using a sealed hierarchy allows exhaustive pattern matching in the command handler.</p>
 */
public sealed interface BookingCommand
        permits CreateBookingCommand, ConfirmBookingCommand, CancelBookingCommand {

    /**
     * The booking ID this command targets (null for create commands).
     */
    default String bookingId() {
        return null;
    }
}
