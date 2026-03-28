package com.freightflow.billingservice.application.query;

import com.freightflow.billingservice.domain.model.Invoice;
import com.freightflow.billingservice.domain.port.InvoiceRepository;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CQRS Query Handler — handles all <b>read</b> operations for the billing domain.
 *
 * <p>This is the <b>read side</b> of CQRS. It queries invoice data including
 * invoice details, payment status, and customer billing history.</p>
 *
 * <h3>Separation from Write Side</h3>
 * <p>The write side ({@link com.freightflow.billingservice.application.command.BillingCommandHandler})
 * handles commands and produces events. This query handler reads the persisted state.
 * The two sides can be scaled independently.</p>
 *
 * @see com.freightflow.billingservice.application.command.BillingCommandHandler
 */
@Service
public class BillingQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingQueryHandler.class);

    private final InvoiceRepository invoiceRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param invoiceRepository the domain port for invoice persistence
     */
    public BillingQueryHandler(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository,
                "invoiceRepository must not be null");
    }

    /**
     * Retrieves an invoice by its ID.
     *
     * <h3>Spring Advanced Feature: @Cacheable</h3>
     * <p>Results are cached in the "invoices" cache region. Cache is evicted
     * when the invoice state is updated by the command handler.</p>
     *
     * @param invoiceId the invoice identifier
     * @return the invoice
     * @throws ResourceNotFoundException if no invoice exists for the given ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "invoices", key = "#invoiceId", unless = "#result == null")
    @Profiled(value = "getInvoice", slowThresholdMs = 200)
    public Invoice getInvoice(UUID invoiceId) {
        log.debug("Querying invoice: invoiceId={}", invoiceId);

        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> {
                    log.warn("Invoice not found: invoiceId={}", invoiceId);
                    return new ResourceNotFoundException("Invoice", invoiceId.toString());
                });
    }

    /**
     * Retrieves all invoices for a booking.
     *
     * @param bookingId the booking identifier
     * @return list of invoices for the booking (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Profiled(value = "getInvoicesByBooking", slowThresholdMs = 300)
    public List<Invoice> getInvoicesByBooking(UUID bookingId) {
        log.debug("Querying invoices by booking: bookingId={}", bookingId);

        List<Invoice> invoices = invoiceRepository.findByBookingId(bookingId);
        log.debug("Found {} invoice(s) for booking: bookingId={}", invoices.size(), bookingId);

        return invoices;
    }

    /**
     * Retrieves all invoices for a customer.
     *
     * @param customerId the customer identifier
     * @return list of invoices for the customer (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "customer-invoices", key = "#customerId", unless = "#result.isEmpty()")
    @Profiled(value = "getInvoicesByCustomer", slowThresholdMs = 300)
    public List<Invoice> getInvoicesByCustomer(UUID customerId) {
        log.debug("Querying invoices by customer: customerId={}", customerId);

        List<Invoice> invoices = invoiceRepository.findByCustomerId(customerId);
        log.debug("Found {} invoice(s) for customer: customerId={}", invoices.size(), customerId);

        return invoices;
    }
}
