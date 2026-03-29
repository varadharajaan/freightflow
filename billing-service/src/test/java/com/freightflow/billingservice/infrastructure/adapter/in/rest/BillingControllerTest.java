package com.freightflow.billingservice.infrastructure.adapter.in.rest;

import com.freightflow.billingservice.application.command.BillingCommandHandler;
import com.freightflow.billingservice.application.query.BillingQueryHandler;
import com.freightflow.billingservice.domain.model.Invoice;
import com.freightflow.billingservice.domain.model.LineItem;
import com.freightflow.billingservice.domain.model.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BillingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BillingQueryHandler billingQueryHandler;

    @MockBean
    private BillingCommandHandler billingCommandHandler;

    @Test
    void shouldReturnInvoiceById() throws Exception {
        Invoice invoice = Invoice.create(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().plusDays(7));
        invoice.addLineItem(LineItem.of("Freight", 1, Money.usd(new BigDecimal("99.99"))));
        UUID invoiceId = invoice.getInvoiceId();

        when(billingQueryHandler.getInvoice(invoiceId)).thenReturn(invoice);

        mockMvc.perform(get("/api/v1/billing/invoices/{invoiceId}", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }
}
