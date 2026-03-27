package com.freightflow.booking.application.command;

import com.freightflow.booking.domain.model.ContainerType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Command to create a new booking in DRAFT status.
 *
 * @param customerId              the customer placing the booking
 * @param origin                  origin port code (UN/LOCODE, e.g., "DEHAM")
 * @param destination             destination port code (UN/LOCODE, e.g., "CNSHA")
 * @param commodityCode           HS commodity classification code
 * @param description             human-readable cargo description
 * @param weightKg                cargo weight in kilograms
 * @param containerType           type of container required
 * @param containerCount          number of containers needed
 * @param requestedDepartureDate  desired departure date
 */
public record CreateBookingCommand(
        String customerId,
        String origin,
        String destination,
        String commodityCode,
        String description,
        BigDecimal weightKg,
        ContainerType containerType,
        int containerCount,
        LocalDate requestedDepartureDate
) implements BookingCommand {}
