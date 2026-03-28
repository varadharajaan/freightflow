package com.freightflow.billingservice.infrastructure.adapter.out.persistence.repository;

import com.freightflow.billingservice.infrastructure.adapter.out.persistence.entity.InvoiceJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for invoice entities.
 *
 * <p>This is a <b>technology-specific</b> interface that provides data access
 * for the billing bounded context. It is NOT the domain port — the domain port
 * is {@link com.freightflow.billingservice.domain.port.InvoiceRepository}.</p>
 *
 * <h3>Features Demonstrated</h3>
 * <ol>
 *   <li>Derived query methods</li>
 *   <li>JPQL queries via {@code @Query}</li>
 *   <li>Native SQL queries for financial aggregations</li>
 *   <li>Bulk {@code @Modifying} operations</li>
 *   <li>Pagination</li>
 * </ol>
 *
 * @see com.freightflow.billingservice.domain.port.InvoiceRepository
 */
@Repository
public interface SpringDataInvoiceRepository
        extends JpaRepository<InvoiceJpaEntity, UUID> {

    // ==================== Derived Query Methods ====================

    /**
     * Finds all invoices for a booking, ordered by creation date.
     *
     * @param bookingId the booking UUID
     * @return invoices for the booking
     */
    List<InvoiceJpaEntity> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Finds all invoices for a customer, ordered by creation date.
     *
     * @param customerId the customer UUID
     * @return invoices for the customer
     */
    List<InvoiceJpaEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /**
     * Finds all invoices in a specific status.
     *
     * @param status the invoice status
     * @return invoices in the specified status
     */
    List<InvoiceJpaEntity> findByStatusOrderByDueDateAsc(String status);

    /**
     * Finds invoices by status with pagination.
     *
     * @param status   the invoice status
     * @param pageable pagination parameters
     * @return a page of matching invoices
     */
    Page<InvoiceJpaEntity> findByStatus(String status, Pageable pageable);

    /**
     * Checks whether an invoice exists for a booking.
     *
     * @param bookingId the booking UUID
     * @return true if at least one invoice exists
     */
    boolean existsByBookingId(UUID bookingId);

    /**
     * Counts invoices by customer and status.
     *
     * @param customerId the customer UUID
     * @param status     the invoice status
     * @return count of matching invoices
     */
    long countByCustomerIdAndStatus(UUID customerId, String status);

    // ==================== JPQL Queries ====================

    /**
     * Finds overdue invoices (ISSUED status past due date).
     *
     * @param today the current date for comparison
     * @return overdue invoices
     */
    @Query("SELECT i FROM InvoiceJpaEntity i WHERE i.status = 'ISSUED' AND i.dueDate < :today ORDER BY i.dueDate ASC")
    List<InvoiceJpaEntity> findOverdueInvoices(@Param("today") LocalDate today);

    /**
     * Calculates total revenue for a customer.
     *
     * @param customerId the customer UUID
     * @return total paid amount (null if no payments)
     */
    @Query("SELECT SUM(i.totalAmount) FROM InvoiceJpaEntity i WHERE i.customerId = :customerId AND i.status = 'PAID'")
    BigDecimal calculateTotalRevenueByCustomer(@Param("customerId") UUID customerId);

    // ==================== Native SQL Queries ====================

    /**
     * Retrieves billing statistics per customer.
     *
     * @param customerId the customer UUID
     * @return raw result arrays of [status, count, totalAmount]
     */
    @Query(value = """
            SELECT i.status, COUNT(*) as invoice_count,
                   COALESCE(SUM(i.total_amount), 0) as total_amount
            FROM invoices i
            WHERE i.customer_id = :customerId
            GROUP BY i.status
            """, nativeQuery = true)
    List<Object[]> getCustomerBillingStatistics(@Param("customerId") UUID customerId);

    /**
     * Finds top customers by revenue in a period.
     *
     * @param since only include invoices created after this timestamp
     * @param limit maximum number of results
     * @return raw result arrays of [customerId, invoiceCount, totalRevenue]
     */
    @Query(value = """
            SELECT i.customer_id, COUNT(*) as invoice_count,
                   SUM(i.total_amount) as total_revenue
            FROM invoices i
            WHERE i.status = 'PAID'
            AND i.created_at >= :since
            GROUP BY i.customer_id
            ORDER BY total_revenue DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopCustomersByRevenue(@Param("since") Instant since, @Param("limit") int limit);

    // ==================== @Modifying Bulk Operations ====================

    /**
     * Bulk-marks issued invoices past due date as overdue.
     *
     * @param today the current date
     * @return the number of rows updated
     */
    @Modifying
    @Query("UPDATE InvoiceJpaEntity i SET i.status = 'OVERDUE', i.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE i.status = 'ISSUED' AND i.dueDate < :today")
    int bulkMarkOverdue(@Param("today") LocalDate today);
}
