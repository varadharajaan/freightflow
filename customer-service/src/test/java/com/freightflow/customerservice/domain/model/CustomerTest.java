package com.freightflow.customerservice.domain.model;

import com.freightflow.commons.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    @Test
    void shouldAllocateAndReleaseCreditWithinLimit() {
        Customer customer = Customer.register(
                "Acme Logistics",
                "ops@acme.test",
                "+1-555-1000",
                new Address("1 Main St", "Seattle", "WA", "98101", "US"),
                CustomerType.SHIPPER,
                Money.of(new BigDecimal("1000.00"), "USD")
        );

        customer.allocateCredit(Money.of(new BigDecimal("250.00"), "USD"));
        customer.releaseCredit(Money.of(new BigDecimal("50.00"), "USD"));

        assertThat(customer.getCurrentCreditUsed().amount()).isEqualByComparingTo("200.00");
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }
}
