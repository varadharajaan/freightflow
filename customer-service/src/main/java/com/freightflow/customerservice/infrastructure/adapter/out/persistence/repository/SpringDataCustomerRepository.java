package com.freightflow.customerservice.infrastructure.adapter.out.persistence.repository;

import com.freightflow.customerservice.infrastructure.adapter.out.persistence.entity.CustomerJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for customer entities — showcases advanced
 * Spring Data JPA features including derived queries, JPQL, native SQL,
 * and pagination.
 *
 * <p>This is a <b>technology-specific</b> interface that extends multiple Spring Data
 * super-interfaces. It is NOT the domain port — the domain port is
 * {@link com.freightflow.customerservice.domain.port.CustomerRepository}.</p>
 *
 * <h3>Features Demonstrated</h3>
 * <ol>
 *   <li>Derived query methods (method name parsing)</li>
 *   <li>JPQL queries via {@code @Query}</li>
 *   <li>Native SQL queries via {@code @Query(nativeQuery = true)}</li>
 *   <li>Bulk {@code @Modifying} operations</li>
 *   <li>Pagination ({@link Page})</li>
 *   <li>Existence checks and count queries</li>
 * </ol>
 *
 * @see com.freightflow.customerservice.domain.port.CustomerRepository
 */
@Repository
public interface SpringDataCustomerRepository
        extends JpaRepository<CustomerJpaEntity, UUID>,
                JpaSpecificationExecutor<CustomerJpaEntity> {

    // ==================== Derived Query Methods ====================

    /**
     * Finds a customer by email address.
     *
     * <p><b>JPA Feature:</b> Derived query method — Spring Data parses the method name
     * into a JPQL query with an equality predicate on the email column.</p>
     *
     * @param email the email address to search for
     * @return the customer, or empty if not found
     */
    Optional<CustomerJpaEntity> findByEmail(String email);

    /**
     * Finds all customers with a given status, ordered by company name.
     *
     * <p><b>JPA Feature:</b> Derived query with ordering — generates a query with
     * a WHERE clause on status and an ORDER BY clause on companyName.</p>
     *
     * @param status the customer status to filter by
     * @return customers in the given status, ordered alphabetically
     */
    List<CustomerJpaEntity> findByStatusOrderByCompanyNameAsc(String status);

    /**
     * Finds customers by type and status.
     *
     * <p><b>JPA Feature:</b> Derived query with {@code And} keyword — generates
     * a WHERE clause with two equality predicates.</p>
     *
     * @param customerType the customer type
     * @param status       the customer status
     * @return matching customers
     */
    List<CustomerJpaEntity> findByCustomerTypeAndStatus(String customerType, String status);

    /**
     * Checks whether a customer exists with the given email.
     *
     * <p><b>JPA Feature:</b> Existence-check derived query — returns a boolean
     * and translates to SQL {@code SELECT EXISTS(...)}.</p>
     *
     * @param email the email to check
     * @return true if a customer exists with this email
     */
    boolean existsByEmail(String email);

    /**
     * Counts customers in a specific status.
     *
     * <p><b>JPA Feature:</b> Count derived query — generates {@code SELECT COUNT(*)}.</p>
     *
     * @param status the customer status
     * @return the count of matching customers
     */
    long countByStatus(String status);

    /**
     * Finds the most recently registered customers in a given status.
     *
     * <p><b>JPA Feature:</b> Top limiting with ordering.</p>
     *
     * @param status the customer status
     * @return up to 10 most recently registered customers
     */
    List<CustomerJpaEntity> findTop10ByStatusOrderByRegisteredAtDesc(String status);

    // ==================== JPQL Queries ====================

    /**
     * Searches customers by company name (case-insensitive partial match).
     *
     * <p><b>JPA Feature:</b> JPQL with LOWER function and LIKE for case-insensitive
     * partial matching. The search fragment is wrapped with '%' wildcards.</p>
     *
     * @param companyNameFragment the search fragment (should be lowercase)
     * @return matching customers ordered by company name
     */
    @Query("SELECT c FROM CustomerJpaEntity c WHERE LOWER(c.companyName) LIKE LOWER(CONCAT('%', :fragment, '%')) ORDER BY c.companyName")
    List<CustomerJpaEntity> searchByCompanyName(@Param("fragment") String companyNameFragment);

    /**
     * Finds customers whose credit usage exceeds a given percentage of their limit.
     *
     * <p><b>JPA Feature:</b> JPQL with arithmetic expressions — computes the credit
     * utilization ratio inline in the query.</p>
     *
     * @param threshold the credit utilization threshold (0.0 to 1.0)
     * @return customers exceeding the threshold
     */
    @Query("SELECT c FROM CustomerJpaEntity c WHERE c.currentCreditUsedAmount / c.creditLimitAmount >= :threshold AND c.status = 'ACTIVE' ORDER BY c.companyName")
    List<CustomerJpaEntity> findCustomersExceedingCreditThreshold(@Param("threshold") BigDecimal threshold);

    /**
     * Finds customers by status with pagination.
     *
     * <p><b>JPA Feature:</b> JPQL combined with Pageable for offset-based pagination.</p>
     *
     * @param status   the customer status
     * @param pageable pagination parameters
     * @return a page of matching customers
     */
    @Query("SELECT c FROM CustomerJpaEntity c WHERE c.status = :status")
    Page<CustomerJpaEntity> findByStatusPaged(@Param("status") String status, Pageable pageable);

    // ==================== Native SQL Queries ====================

    /**
     * Finds customer credit summaries using native PostgreSQL query.
     *
     * <p><b>JPA Feature:</b> Native SQL with aggregate functions — computes
     * per-type statistics in a single round-trip.</p>
     *
     * @return raw result arrays of [customer_type, count, total_credit_limit, total_credit_used]
     */
    @Query(value = """
            SELECT c.customer_type, COUNT(*) as customer_count,
                   SUM(c.credit_limit_amount) as total_credit_limit,
                   SUM(c.current_credit_used_amount) as total_credit_used
            FROM customers c
            WHERE c.status = 'ACTIVE'
            GROUP BY c.customer_type
            ORDER BY customer_count DESC
            """, nativeQuery = true)
    List<Object[]> getCreditSummaryByType();

    /**
     * Finds customers registered within a date range using native SQL.
     *
     * <p><b>JPA Feature:</b> Native SQL with BETWEEN clause for date range filtering.</p>
     *
     * @param from the start timestamp (inclusive)
     * @param to   the end timestamp (inclusive)
     * @return customers registered in the date range
     */
    @Query(value = """
            SELECT c.* FROM customers c
            WHERE c.registered_at BETWEEN :from AND :to
            ORDER BY c.registered_at DESC
            """, nativeQuery = true)
    List<CustomerJpaEntity> findRegisteredBetween(
            @Param("from") Instant from, @Param("to") Instant to);

    // ==================== @Modifying Bulk Operations ====================

    /**
     * Bulk-updates the status of customers matching criteria.
     *
     * <p><b>JPA Feature:</b> {@code @Modifying} JPQL UPDATE for batch state changes.</p>
     *
     * @param oldStatus the current status to match
     * @param newStatus the new status to set
     * @return the number of rows updated
     */
    @Modifying
    @Query("UPDATE CustomerJpaEntity c SET c.status = :newStatus, c.updatedAt = CURRENT_TIMESTAMP WHERE c.status = :oldStatus")
    int bulkUpdateStatus(@Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);
}
