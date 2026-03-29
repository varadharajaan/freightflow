package com.freightflow.customerservice.infrastructure.adapter.in.rest;

import com.freightflow.commons.domain.Money;
import com.freightflow.customerservice.application.command.CustomerCommandHandler;
import com.freightflow.customerservice.application.query.CustomerQueryHandler;
import com.freightflow.customerservice.domain.model.Address;
import com.freightflow.customerservice.domain.model.Customer;
import com.freightflow.customerservice.domain.model.CustomerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerCommandHandler customerCommandHandler;

    @MockBean
    private CustomerQueryHandler customerQueryHandler;

    @Test
    void shouldReturnCustomerById() throws Exception {
        Customer customer = Customer.register(
                "Acme Logistics",
                "ops@acme.test",
                "+1-555-2000",
                new Address("1 Main St", "Seattle", "WA", "98101", "US"),
                CustomerType.SHIPPER,
                Money.of(new BigDecimal("1000.00"), "USD")
        );

        when(customerQueryHandler.getCustomer(customer.getCustomerId().asString())).thenReturn(customer);

        mockMvc.perform(get("/api/v1/customers/{customerId}", customer.getCustomerId().asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.getCustomerId().asString()))
                .andExpect(jsonPath("$.companyName").value("Acme Logistics"));
    }
}
