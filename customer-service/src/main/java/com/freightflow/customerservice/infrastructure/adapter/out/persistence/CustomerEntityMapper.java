package com.freightflow.customerservice.infrastructure.adapter.out.persistence;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.Money;
import com.freightflow.customerservice.domain.model.Address;
import com.freightflow.customerservice.domain.model.Customer;
import com.freightflow.customerservice.domain.model.CustomerStatus;
import com.freightflow.customerservice.domain.model.CustomerType;
import com.freightflow.customerservice.infrastructure.adapter.out.persistence.entity.CustomerJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Maps between the JPA entity ({@link CustomerJpaEntity}) and the domain model ({@link Customer}).
 *
 * <p>This mapper is the translation layer between the persistence world (JPA annotations,
 * flat columns) and the domain world (aggregates, value objects). It ensures the domain
 * model stays completely free of persistence concerns (Dependency Inversion Principle).</p>
 *
 * <p>A hand-written mapper is used because the Customer aggregate has a private constructor,
 * domain events, and complex value objects (Address, Money, CustomerId) that require
 * careful flattening and reconstruction.</p>
 *
 * @see CustomerJpaEntity
 * @see Customer
 */
@Component
public class CustomerEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(CustomerEntityMapper.class);

    /**
     * Converts a domain {@link Customer} aggregate to a {@link CustomerJpaEntity} for persistence.
     *
     * @param customer the domain customer aggregate
     * @return the JPA entity ready for persistence
     */
    public CustomerJpaEntity toEntity(Customer customer) {
        log.trace("Mapping domain Customer to JPA entity: customerId={}",
                customer.getCustomerId().asString());

        var entity = new CustomerJpaEntity();
        entity.setId(customer.getCustomerId().value());
        entity.setCompanyName(customer.getCompanyName());
        entity.setEmail(customer.getEmail());
        entity.setPhone(customer.getPhone());

        // Address fields (flattened from value object, nullable)
        Address address = customer.getAddress();
        if (address != null) {
            entity.setAddressStreet(address.street());
            entity.setAddressCity(address.city());
            entity.setAddressState(address.state());
            entity.setAddressPostalCode(address.postalCode());
            entity.setAddressCountry(address.country());
        }

        // Type and status
        entity.setCustomerType(customer.getCustomerType().name());
        entity.setStatus(customer.getStatus().name());

        // Credit fields (flattened from Money value objects)
        entity.setCreditLimitAmount(customer.getCreditLimit().amount());
        entity.setCreditLimitCurrency(customer.getCreditLimit().currency().getCurrencyCode());
        entity.setCurrentCreditUsedAmount(customer.getCurrentCreditUsed().amount());
        entity.setCurrentCreditUsedCurrency(customer.getCurrentCreditUsed().currency().getCurrencyCode());

        // Audit fields
        entity.setRegisteredAt(customer.getRegisteredAt());
        entity.setCreatedAt(customer.getRegisteredAt()); // createdAt mirrors registeredAt
        entity.setUpdatedAt(customer.getUpdatedAt());
        entity.setVersion(customer.getVersion());

        return entity;
    }

    /**
     * Reconstructs a domain {@link Customer} aggregate from a {@link CustomerJpaEntity}.
     *
     * <p>Uses {@link Customer#reconstitute} to rebuild the aggregate from persisted state
     * without triggering domain events. Address and Money value objects are reconstructed
     * from the flat entity columns. Null-safe handling is applied for optional address fields.</p>
     *
     * @param entity the JPA entity loaded from the database
     * @return the domain customer aggregate in its persisted state
     */
    public Customer toDomain(CustomerJpaEntity entity) {
        log.trace("Mapping JPA entity to domain Customer: customerId={}", entity.getId());

        // Reconstruct Address value object if all required fields are present
        Address address = null;
        if (entity.getAddressStreet() != null && entity.getAddressCity() != null
                && entity.getAddressState() != null && entity.getAddressPostalCode() != null
                && entity.getAddressCountry() != null) {
            address = new Address(
                    entity.getAddressStreet(),
                    entity.getAddressCity(),
                    entity.getAddressState(),
                    entity.getAddressPostalCode(),
                    entity.getAddressCountry()
            );
        }

        // Reconstruct Money value objects for credit
        Money creditLimit = new Money(
                entity.getCreditLimitAmount() != null ? entity.getCreditLimitAmount() : BigDecimal.ZERO,
                Currency.getInstance(entity.getCreditLimitCurrency())
        );
        Money currentCreditUsed = new Money(
                entity.getCurrentCreditUsedAmount() != null ? entity.getCurrentCreditUsedAmount() : BigDecimal.ZERO,
                Currency.getInstance(entity.getCurrentCreditUsedCurrency())
        );

        return Customer.reconstitute(
                new CustomerId(entity.getId()),
                entity.getCompanyName(),
                entity.getEmail(),
                entity.getPhone(),
                address,
                CustomerType.valueOf(entity.getCustomerType()),
                CustomerStatus.valueOf(entity.getStatus()),
                creditLimit,
                currentCreditUsed,
                entity.getRegisteredAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
