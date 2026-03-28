package com.freightflow.customerservice.infrastructure.adapter.in.rest.dto;

import com.freightflow.customerservice.domain.model.CustomerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Inbound REST DTO for registering a new customer.
 *
 * <p>Validated at the controller layer via Jakarta Bean Validation before
 * being passed to the application layer command handler. This DTO is
 * intentionally decoupled from the domain to allow the API contract to
 * evolve independently.</p>
 *
 * @param companyName    the company name
 * @param email          the contact email address
 * @param phone          the contact phone number
 * @param address        the physical address
 * @param customerType   the customer type (SHIPPER, CONSIGNEE, FREIGHT_FORWARDER)
 * @param creditLimitUsd the initial credit limit in USD
 */
public record CreateCustomerRequest(

        @NotBlank(message = "Company name is required")
        String companyName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        String phone,

        @Valid
        AddressDto address,

        @NotNull(message = "Customer type is required")
        CustomerType customerType,

        @NotNull(message = "Credit limit is required")
        @Positive(message = "Credit limit must be positive")
        BigDecimal creditLimitUsd
) {

    /**
     * Nested DTO for address validation.
     *
     * @param street     the street address
     * @param city       the city
     * @param state      the state or province
     * @param postalCode the postal code
     * @param country    the 2-letter country code
     */
    public record AddressDto(

            @NotBlank(message = "Street is required")
            String street,

            @NotBlank(message = "City is required")
            String city,

            @NotBlank(message = "State is required")
            String state,

            @NotBlank(message = "Postal code is required")
            String postalCode,

            @NotBlank(message = "Country is required")
            String country
    ) {
    }
}
