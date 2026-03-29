package com.freightflow.customerservice.infrastructure.adapter.out.persistence;

import com.freightflow.customerservice.infrastructure.adapter.out.persistence.entity.CustomerJpaEntity;
import com.freightflow.customerservice.infrastructure.adapter.out.persistence.repository.SpringDataCustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.enabled=false")
class CustomerRepositoryIntegrationTest {

    @Autowired
    private SpringDataCustomerRepository customerRepository;

    @Test
    void shouldPersistAndFindCustomerByEmail() {
        CustomerJpaEntity customer = new CustomerJpaEntity();
        customer.setId(UUID.randomUUID());
        customer.setCompanyName("Acme Logistics");
        customer.setEmail("ops@acme.test");
        customer.setPhone("+1-555-3000");
        customer.setAddressStreet("1 Main St");
        customer.setAddressCity("Seattle");
        customer.setAddressState("WA");
        customer.setAddressPostalCode("98101");
        customer.setAddressCountry("US");
        customer.setCustomerType("SHIPPER");
        customer.setStatus("ACTIVE");
        customer.setCreditLimitAmount(new BigDecimal("1000.00"));
        customer.setCreditLimitCurrency("USD");
        customer.setCurrentCreditUsedAmount(BigDecimal.ZERO);
        customer.setCurrentCreditUsedCurrency("USD");
        customer.setRegisteredAt(Instant.now());
        customer.setCreatedAt(Instant.now());
        customer.setUpdatedAt(Instant.now());
        customer.setVersion(0);

        customerRepository.saveAndFlush(customer);

        Optional<CustomerJpaEntity> found = customerRepository.findByEmail("ops@acme.test");
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getCompanyName()).isEqualTo("Acme Logistics");
    }
}
