package com.freightflow.booking.infrastructure.adapter.in.rest.dto;

import com.freightflow.booking.application.BookingService;
import com.freightflow.booking.domain.model.ContainerType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound REST DTO for creating a new booking.
 *
 * <p>Validated at the controller layer via Jakarta Bean Validation before
 * being transformed into a {@link BookingService.CreateBookingCommand} for
 * the application layer. This DTO is intentionally decoupled from the domain
 * to allow the API contract to evolve independently.</p>
 *
 * @param customerId             UUID of the customer placing the booking
 * @param origin                 origin port code (UN/LOCODE, e.g. "CNSHA")
 * @param destination            destination port code (UN/LOCODE, e.g. "USLAX")
 * @param commodityCode          HS commodity classification code
 * @param description            human-readable cargo description
 * @param weightKg               total cargo weight in kilograms
 * @param containerType          type of container required
 * @param containerCount         number of containers needed
 * @param requestedDepartureDate desired departure date (must be in the future)
 */
public record CreateBookingRequest(

        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotBlank(message = "Origin port code is required")
        String origin,

        @NotBlank(message = "Destination port code is required")
        String destination,

        @NotBlank(message = "Commodity code is required")
        String commodityCode,

        @NotBlank(message = "Cargo description is required")
        String description,

        @NotNull(message = "Weight is required")
        @Positive(message = "Weight must be positive")
        BigDecimal weightKg,

        @NotNull(message = "Container type is required")
        ContainerType containerType,

        @NotNull(message = "Container count is required")
        @Positive(message = "Container count must be positive")
        Integer containerCount,

        @NotNull(message = "Requested departure date is required")
        @Future(message = "Requested departure date must be in the future")
        LocalDate requestedDepartureDate
) {

    /**
     * Converts this REST request into an application-layer command.
     *
     * <p>This transformation isolates the REST API contract from the
     * application layer's command model, following the Anti-Corruption Layer pattern.</p>
     *
     * @return a new {@link BookingService.CreateBookingCommand}
     */
    public BookingService.CreateBookingCommand toCommand() {
        return new BookingService.CreateBookingCommand(
                customerId,
                origin,
                destination,
                commodityCode,
                description,
                weightKg,
                containerType,
                containerCount,
                requestedDepartureDate
        );
    }
}
