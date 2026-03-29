package com.freightflow.billingservice.infrastructure.adapter.out.persistence;

import com.freightflow.billingservice.infrastructure.adapter.out.persistence.entity.InvoiceJpaEntity;
import com.freightflow.billingservice.infrastructure.adapter.out.persistence.repository.SpringDataInvoiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.enabled=false")
class InvoiceRepositoryIntegrationTest {

    @Autowired
    private SpringDataInvoiceRepository invoiceRepository;

    @Test
    void shouldPersistAndQueryInvoiceByCustomerId() {
        UUID customerId = UUID.randomUUID();

        InvoiceJpaEntity invoice = new InvoiceJpaEntity();
        invoice.setId(UUID.randomUUID());
        invoice.setBookingId(UUID.randomUUID());
        invoice.setCustomerId(customerId);
        invoice.setStatus("DRAFT");
        invoice.setTotalAmount(new BigDecimal("123.45"));
        invoice.setCurrency("USD");
        invoice.setDueDate(LocalDate.now().plusDays(7));
        invoice.setCreatedAt(Instant.now());
        invoice.setUpdatedAt(Instant.now());
        invoice.setVersion(0);

        invoiceRepository.saveAndFlush(invoice);

        List<InvoiceJpaEntity> results = invoiceRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getCurrency()).isEqualTo("USD");
    }
}
