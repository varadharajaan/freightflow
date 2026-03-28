package com.freightflow.billingservice.domain.port;

import com.freightflow.billingservice.domain.model.Invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for invoice persistence.
 *
 * <p>This interface defines the contract that the domain layer expects from
 * the persistence layer (Dependency Inversion Principle). The domain does NOT
 * depend on JPA, Hibernate, or any infrastructure technology.</p>
 *
 * <p>The implementation (JPA adapter) lives in the infrastructure layer
 * and adapts this port to Spring Data JPA.</p>
 *
 * @see com.freightflow.billingservice.infrastructure.adapter.out.persistence
 */
public interface InvoiceRepository {

    /**
     * Persists a new or updated invoice.
     *
     * @param invoice the invoice aggregate to save
     * @return the saved invoice (with updated version)
     */
    Invoice save(Invoice invoice);

    /**
     * Finds an invoice by its ID.
     *
     * @param invoiceId the invoice identifier
     * @return the invoice, or empty if not found
     */
    Optional<Invoice> findById(UUID invoiceId);

    /**
     * Finds all invoices for a booking.
     *
     * @param bookingId the booking identifier
     * @return list of invoices (may be empty)
     */
    List<Invoice> findByBookingId(UUID bookingId);

    /**
     * Finds all invoices for a customer.
     *
     * @param customerId the customer identifier
     * @return list of invoices (may be empty)
     */
    List<Invoice> findByCustomerId(UUID customerId);

    /**
     * Checks whether an invoice exists.
     *
     * @param invoiceId the invoice identifier
     * @return true if the invoice exists
     */
    boolean existsById(UUID invoiceId);
}
