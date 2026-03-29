package com.freightflow.billingservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTest {

    @Test
    void shouldTransitionFromDraftToPaidAfterIssueAndPayment() {
        Invoice invoice = Invoice.create(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().plusDays(5));
        invoice.addLineItem(LineItem.of("Ocean freight", 2, Money.usd(new BigDecimal("100.00"))));
        invoice.issue();

        invoice.recordPayment(new Payment(
                UUID.randomUUID(),
                Money.usd(new BigDecimal("200.00")),
                Payment.PaymentMethod.BANK_TRANSFER,
                Instant.now(),
                "PAY-REF-001"
        ));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getTotalAmount().amount()).isEqualByComparingTo("200.00");
    }
}
