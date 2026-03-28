package com.freightflow.booking.infrastructure.adapter.out.persistence.repository;

import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;

import java.util.List;
import java.util.UUID;

/**
 * Custom repository fragment interface for booking queries that cannot be expressed
 * using Spring Data's derived query methods, JPQL, or native SQL.
 *
 * <h3>Custom Repository Fragment Pattern</h3>
 * <p>Spring Data JPA allows a repository to be composed of multiple <b>fragments</b>.
 * Each fragment is defined by:</p>
 * <ol>
 *   <li>An interface declaring the custom methods (this interface)</li>
 *   <li>An implementation class named {@code <InterfaceName>Impl} by convention
 *       ({@link BookingCustomRepositoryImpl})</li>
 * </ol>
 *
 * <p>The main repository interface ({@link SpringDataBookingRepository}) extends this
 * interface alongside {@code JpaRepository}, {@code JpaSpecificationExecutor}, etc.
 * Spring Data automatically detects the {@code Impl} class and merges all fragment
 * implementations into a single proxy at runtime.</p>
 *
 * <h3>When to use custom fragments vs. other approaches</h3>
 * <table>
 *   <tr><th>Approach</th><th>Best for</th></tr>
 *   <tr><td>Derived queries</td><td>Simple, single-entity lookups by field(s)</td></tr>
 *   <tr><td>JPQL / @Query</td><td>Joins, aggregates, projections known at compile time</td></tr>
 *   <tr><td>Specifications</td><td>Reusable, composable predicates (AND/OR)</td></tr>
 *   <tr><td><b>Custom fragments</b></td><td>Complex dynamic queries (Criteria API),
 *       stored procedures, native JDBC, or aggregation pipelines</td></tr>
 * </table>
 *
 * @see BookingCustomRepositoryImpl
 * @see SpringDataBookingRepository
 */
public interface BookingCustomRepository {

    /**
     * Dynamically searches for bookings matching the given criteria.
     *
     * <p>Uses the JPA Criteria API to build a type-safe, dynamic query where each
     * filter in {@link BookingSearchCriteria} is only applied when present. This is
     * the preferred approach when the number of optional filters exceeds what
     * {@link org.springframework.data.jpa.domain.Specification} can cleanly express.</p>
     *
     * @param criteria the search criteria with optional filters
     * @return matching booking entities, ordered by creation date descending
     * @see BookingSearchCriteria
     */
    List<BookingJpaEntity> searchBookings(BookingSearchCriteria criteria);

    /**
     * Calculates aggregated statistics for a customer's bookings.
     *
     * <p>Uses the JPA Criteria API aggregate functions ({@code count}, {@code sum},
     * {@code avg}) to compute summary metrics without loading individual entities.
     * This is significantly more efficient than loading all entities and computing
     * in memory.</p>
     *
     * @param customerId the customer UUID to calculate statistics for
     * @return aggregated booking statistics, or empty statistics if no bookings exist
     * @see BookingStatistics
     */
    BookingStatistics calculateStatistics(UUID customerId);
}
