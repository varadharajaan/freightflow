package com.freightflow.customerservice.infrastructure.adapter.in.rest.dto;

import com.freightflow.customerservice.domain.model.Customer;
import com.freightflow.customerservice.domain.model.CustomerStatus;
import com.freightflow.customerservice.domain.model.CustomerType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound REST DTO representing a customer in API responses.
 *
 * <p>This record maps from the {@link Customer} domain entity to a flat JSON-serializable
 * structure. It intentionally hides internal domain details (e.g., domain events)
 * from API consumers, following the Interface Segregation Principle.</p>
 *
 * @param customerId       unique customer identifier
 * @param companyName      the company name
 * @param email            the contact email
 * @param phone            the contact phone
 * @param street           address street
 * @param city             address city
 * @param state            address state
 * @param postalCode       address postal code
 * @param country          address country code
 * @param customerType     the customer type
 * @param status           the current customer status
 * @param creditLimitAmount   the credit limit amount
 * @param creditLimitCurrency the credit limit currency code
 * @param creditUsedAmount    the current credit used amount
 * @param registeredAt     when the customer was registered
 * @param updatedAt        when the customer was last modified
 */
public record CustomerResponse(
        String customerId,
        String companyName,
        String email,
        String phone,
        String street,
        String city,
        String state,
        String postalCode,
        String country,
        CustomerType customerType,
        CustomerStatus status,
        BigDecimal creditLimitAmount,
        String creditLimitCurrency,
        BigDecimal creditUsedAmount,
        Instant registeredAt,
        Instant updatedAt
) {

    /**
     * Factory method that maps a domain {@link Customer} to an API response DTO.
     *
     * <p>Extracts nested value objects (Address, Money) into flat fields
     * suitable for JSON serialization.</p>
     *
     * @param customer the domain customer aggregate
     * @return a new {@code CustomerResponse} DTO
     */
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getCustomerId().asString(),
                customer.getCompanyName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress() != null ? customer.getAddress().street() : null,
                customer.getAddress() != null ? customer.getAddress().city() : null,
                customer.getAddress() != null ? customer.getAddress().state() : null,
                customer.getAddress() != null ? customer.getAddress().postalCode() : null,
                customer.getAddress() != null ? customer.getAddress().country() : null,
                customer.getCustomerType(),
                customer.getStatus(),
                customer.getCreditLimit().amount(),
                customer.getCreditLimit().currency().getCurrencyCode(),
                customer.getCurrentCreditUsed().amount(),
                customer.getRegisteredAt(),
                customer.getUpdatedAt()
        );
    }
}
