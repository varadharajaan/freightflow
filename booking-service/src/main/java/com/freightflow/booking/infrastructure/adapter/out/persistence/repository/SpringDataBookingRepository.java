package com.freightflow.booking.infrastructure.adapter.out.persistence.repository;

import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Spring Data JPA repository for booking entities — showcases <b>all</b> advanced
 * Spring Data JPA features in a single interface.
 *
 * <p>This is a <b>technology-specific</b> interface that extends multiple Spring Data
 * super-interfaces and a custom fragment. It is NOT the domain port — the domain port
 * is {@link com.freightflow.booking.domain.port.BookingRepository}.</p>
 *
 * <p>The {@link JpaBookingPersistenceAdapter} wraps this repository and maps between
 * JPA entities and domain objects.</p>
 *
 * <h3>Inheritance Hierarchy</h3>
 * <ul>
 *   <li>{@link JpaRepository} — standard CRUD + batch operations + flush control + pagination</li>
 *   <li>{@link JpaSpecificationExecutor} — dynamic queries via type-safe JPA Specifications</li>
 *   <li>{@link QueryByExampleExecutor} — query-by-example using probe entity instances</li>
 *   <li>{@link BookingCustomRepository} — custom fragment with Criteria API and aggregation</li>
 * </ul>
 *
 * <h3>Features Demonstrated</h3>
 * <ol>
 *   <li>Derived query methods (method name parsing)</li>
 *   <li>JPQL queries via {@code @Query}</li>
 *   <li>Native SQL queries via {@code @Query(nativeQuery = true)}</li>
 *   <li>Bulk {@code @Modifying} operations (UPDATE, DELETE)</li>
 *   <li>Pagination ({@link Page}) and infinite scrolling ({@link Slice})</li>
 *   <li>Streaming results ({@link Stream}) for large datasets</li>
 *   <li>{@code @EntityGraph} for eager fetching (N+1 prevention)</li>
 *   <li>Interface projections ({@link BookingSummaryProjection})</li>
 *   <li>Named queries (defined on the entity via {@code @NamedQuery})</li>
 *   <li>Top/First result limiting</li>
 *   <li>Distinct queries</li>
 *   <li>Existence checks and count queries</li>
 * </ol>
 *
 * @see JpaBookingPersistenceAdapter
 * @see BookingCustomRepository
 * @see BookingSummaryProjection
 */
@Repository
public interface SpringDataBookingRepository
        extends JpaRepository<BookingJpaEntity, UUID>,
                JpaSpecificationExecutor<BookingJpaEntity>,
                QueryByExampleExecutor<BookingJpaEntity>,
                BookingCustomRepository {

    // ==================== Derived Query Methods ====================
    // Spring Data parses the method name to automatically generate the query.
    // The naming convention follows: find...By<Property><Condition>

    /**
     * Finds all bookings for a customer in a specific status, ordered by most recent first.
     *
     * <p><b>JPA Feature:</b> Derived query method — Spring Data parses the method name
     * into a JPQL query. Multi-field filtering ({@code And}), ordering ({@code OrderBy}),
     * and direction ({@code Desc}) are all inferred from the method name.</p>
     *
     * @param customerId the customer UUID
     * @param status     the booking status to filter by
     * @return bookings matching the criteria, ordered by creation date descending
     */
    List<BookingJpaEntity> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, String status);

    /**
     * Finds bookings in a given status with a requested departure date within a range.
     *
     * <p><b>JPA Feature:</b> Derived query with {@code Between} keyword — generates
     * a range predicate ({@code >= from AND <= to}) on the departure date field.</p>
     *
     * @param status the booking status
     * @param from   the start date (inclusive)
     * @param to     the end date (inclusive)
     * @return bookings in the specified status and date range
     */
    List<BookingJpaEntity> findByStatusAndRequestedDepartureDateBetween(
            String status, LocalDate from, LocalDate to);

    /**
     * Finds bookings for a route (origin to destination), excluding a specific status.
     *
     * <p><b>JPA Feature:</b> Derived query with {@code Not} keyword — generates
     * a negation predicate ({@code != status}) to exclude cancelled or other bookings.</p>
     *
     * @param origin      the origin port code
     * @param destination the destination port code
     * @param status      the status to exclude
     * @return bookings matching the route but not in the excluded status
     */
    List<BookingJpaEntity> findByOriginPortAndDestinationPortAndStatusNot(
            String origin, String destination, String status);

    /**
     * Finds the most recent booking for a customer in a specific status.
     *
     * <p><b>JPA Feature:</b> Derived query with {@code First} keyword — limits results
     * to one row. Combined with {@code OrderBy...Desc}, this returns the most recent
     * matching booking. Returns {@link Optional} since the customer may have no bookings
     * in the requested status.</p>
     *
     * @param customerId the customer UUID
     * @param status     the booking status
     * @return the most recent booking matching the criteria, or empty
     */
    Optional<BookingJpaEntity> findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(
            UUID customerId, String status);

    /**
     * Finds bookings exceeding a container count threshold with containers of specific types.
     *
     * <p><b>JPA Feature:</b> Derived query with {@code GreaterThan} and {@code In}
     * keywords — generates a numeric comparison and an IN-list predicate. The {@code In}
     * keyword accepts a {@link List} parameter for multi-value matching.</p>
     *
     * @param minCount the minimum container count (exclusive)
     * @param types    the allowed container type names
     * @return bookings with more than {@code minCount} containers of the specified types
     */
    List<BookingJpaEntity> findByContainerCountGreaterThanAndContainerTypeIn(
            int minCount, List<String> types);

    /**
     * Checks whether any booking exists for a customer in a given status.
     *
     * <p><b>JPA Feature:</b> Existence-check derived query — returns a primitive
     * {@code boolean} instead of loading entities. Translates to a SQL
     * {@code SELECT EXISTS(...)} which is more efficient than {@code COUNT(*) > 0}.</p>
     *
     * @param customerId the customer UUID
     * @param status     the booking status
     * @return true if at least one matching booking exists
     */
    boolean existsByCustomerIdAndStatus(UUID customerId, String status);

    /**
     * Counts bookings in a status that were created after a given timestamp.
     *
     * <p><b>JPA Feature:</b> Count derived query — the {@code count} prefix tells
     * Spring Data to generate a {@code SELECT COUNT(*)} query instead of returning
     * entities. The {@code After} keyword maps to a {@code >} comparison.</p>
     *
     * @param status the booking status
     * @param after  the cutoff timestamp (exclusive)
     * @return the number of matching bookings
     */
    long countByStatusAndCreatedAtAfter(String status, Instant after);

    /**
     * Finds the 10 most recent bookings in a given status.
     *
     * <p><b>JPA Feature:</b> Top/First limiting query — the {@code Top10} keyword
     * appends a {@code LIMIT 10} clause to the generated query. This is more efficient
     * than loading all results and truncating in Java. Can also be written as
     * {@code findFirst10By...}.</p>
     *
     * @param status the booking status
     * @return up to 10 most recent bookings in the specified status
     */
    List<BookingJpaEntity> findTop10ByStatusOrderByCreatedAtDesc(String status);

    /**
     * Finds distinct bookings involving a port as either origin or destination.
     *
     * <p><b>JPA Feature:</b> Distinct query with {@code Or} keyword — the {@code Distinct}
     * keyword adds a {@code SELECT DISTINCT} clause to prevent duplicate rows. The
     * {@code Or} keyword generates a disjunction (OR) between the two port conditions.</p>
     *
     * @param port1 the first port code (matched against origin)
     * @param port2 the second port code (matched against destination)
     * @return distinct bookings where origin equals port1 or destination equals port2
     */
    List<BookingJpaEntity> findDistinctByOriginPortOrDestinationPort(String port1, String port2);

    // ==================== JPQL Queries ====================
    // JPQL (Java Persistence Query Language) operates on entity names and field names,
    // not table/column names. It is database-agnostic and supports JOIN FETCH, subqueries,
    // and aggregation.

    /**
     * Finds all bookings for a customer, ordered by creation date descending.
     *
     * <p><b>JPA Feature:</b> Explicit JPQL query via {@code @Query} — useful when
     * the generated query from a derived method name would be inefficient or when
     * you want to be explicit about the query structure for readability.</p>
     *
     * @param customerId the customer UUID
     * @return customer's bookings ordered by most recent first
     */
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.customerId = :customerId ORDER BY b.createdAt DESC")
    List<BookingJpaEntity> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") UUID customerId);

    /**
     * Finds bookings that are "stale" — in a given status for too long.
     *
     * <p><b>JPA Feature:</b> Parameterized JPQL query — named parameters ({@code :status},
     * {@code :cutoff}) bound via {@code @Param}. This is the recommended approach over
     * positional parameters ({@code ?1}) for readability and refactoring safety.</p>
     *
     * <p>Business use case: find DRAFT bookings that haven't been confirmed within the
     * configured expiry period, so they can be auto-cancelled.</p>
     *
     * @param status the status to check for staleness
     * @param cutoff bookings created before this timestamp are considered stale
     * @return stale bookings in the given status
     */
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.status = :status AND b.createdAt < :cutoff")
    List<BookingJpaEntity> findStaleBookings(@Param("status") String status, @Param("cutoff") Instant cutoff);

    /**
     * Finds all bookings that involve a specific port (as origin or destination).
     *
     * <p><b>JPA Feature:</b> JPQL with OR condition and ordering — demonstrates
     * a single named parameter ({@code :port}) reused in multiple positions within
     * the same query. Results are ordered by requested departure date.</p>
     *
     * @param portCode the UN/LOCODE port code to search for
     * @return bookings involving the port, ordered by departure date
     */
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.originPort = :port OR b.destinationPort = :port "
            + "ORDER BY b.requestedDepartureDate")
    List<BookingJpaEntity> findBookingsInvolvingPort(@Param("port") String portCode);

    /**
     * Finds bookings assigned to a voyage and in one of the specified statuses.
     *
     * <p><b>JPA Feature:</b> JPQL with IN clause — the {@code :statuses} parameter
     * accepts a {@link List} which JPA expands into a SQL IN list. This is safer
     * than string concatenation and prevents SQL injection.</p>
     *
     * @param voyageId the voyage UUID
     * @param statuses the list of acceptable status values
     * @return bookings on the voyage matching one of the statuses
     */
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.voyageId = :voyageId AND b.status IN :statuses")
    List<BookingJpaEntity> findByVoyageIdAndStatuses(
            @Param("voyageId") UUID voyageId, @Param("statuses") List<String> statuses);

    /**
     * Counts bookings for a customer in a specific status.
     *
     * <p><b>JPA Feature:</b> JPQL aggregate query — uses {@code COUNT} to compute a
     * scalar value without loading entities. This is executed as a single SQL query
     * returning a single row with a single column.</p>
     *
     * @param customerId the customer UUID
     * @param status     the booking status
     * @return the count of matching bookings
     */
    @Query("SELECT COUNT(b) FROM BookingJpaEntity b WHERE b.customerId = :customerId AND b.status = :status")
    long countByCustomerIdAndStatus(@Param("customerId") UUID customerId, @Param("status") String status);

    // ==================== Native SQL Queries ====================
    // Native queries bypass JPQL and execute raw SQL against the database.
    // Use when you need database-specific features (window functions, CTEs,
    // INTERVAL arithmetic, etc.) not available in JPQL.

    /**
     * Finds heavy, recently created bookings using a native PostgreSQL query.
     *
     * <p><b>JPA Feature:</b> Native SQL query via {@code @Query(nativeQuery = true)} —
     * this query uses PostgreSQL-specific {@code NOW() - INTERVAL '30 days'} syntax
     * that is not available in JPQL. Native queries operate on table/column names,
     * not entity/field names.</p>
     *
     * <p><b>Trade-off:</b> Native queries sacrifice database portability for access
     * to vendor-specific features. Use sparingly and only when JPQL cannot express
     * the desired query.</p>
     *
     * @param status    the booking status to filter by
     * @param minWeight the minimum weight value (exclusive lower bound)
     * @return heavy recent bookings ordered by weight descending
     */
    @Query(value = """
            SELECT b.* FROM bookings b
            WHERE b.status = :status
            AND b.weight_value > :minWeight
            AND b.created_at >= NOW() - INTERVAL '30 days'
            ORDER BY b.weight_value DESC
            """, nativeQuery = true)
    List<BookingJpaEntity> findHeavyRecentBookings(
            @Param("status") String status, @Param("minWeight") BigDecimal minWeight);

    /**
     * Finds the most popular shipping routes by booking count.
     *
     * <p><b>JPA Feature:</b> Native SQL with GROUP BY and aggregate functions —
     * returns raw {@code Object[]} arrays because the result is not an entity.
     * Each row contains: [origin_port, destination_port, booking_count, total_containers].
     * Consider using a DTO projection or {@code @SqlResultSetMapping} for type safety
     * in production code.</p>
     *
     * @param since only count bookings created after this timestamp
     * @param limit maximum number of routes to return
     * @return raw result arrays of [origin, destination, count, totalContainers]
     */
    @Query(value = """
            SELECT b.origin_port, b.destination_port, COUNT(*) as booking_count,
                   SUM(b.container_count) as total_containers
            FROM bookings b
            WHERE b.status IN ('CONFIRMED', 'SHIPPED')
            AND b.created_at >= :since
            GROUP BY b.origin_port, b.destination_port
            ORDER BY booking_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopRoutes(@Param("since") Instant since, @Param("limit") int limit);

    /**
     * Retrieves per-status booking statistics for a customer via native SQL.
     *
     * <p><b>JPA Feature:</b> Native SQL with multiple aggregate functions — computes
     * COUNT, AVG, and SUM in a single round-trip to the database. Returns
     * {@code Object[]} arrays with: [status, count, avg_containers, total_weight].</p>
     *
     * <p>For a type-safe alternative, see
     * {@link BookingCustomRepository#calculateStatistics(UUID)} which uses the
     * JPA Criteria API.</p>
     *
     * @param customerId the customer UUID
     * @return raw result arrays of [status, count, avgContainers, totalWeight]
     */
    @Query(value = """
            SELECT b.status, COUNT(*) as count,
                   AVG(b.container_count) as avg_containers,
                   SUM(b.weight_value) as total_weight
            FROM bookings b
            WHERE b.customer_id = :customerId
            GROUP BY b.status
            """, nativeQuery = true)
    List<Object[]> getCustomerBookingStatistics(@Param("customerId") UUID customerId);

    // ==================== @Modifying Bulk Operations ====================
    // Bulk UPDATE/DELETE operations bypass the persistence context and execute
    // directly against the database. The @Modifying annotation is REQUIRED for
    // any query that modifies data.

    /**
     * Bulk-updates the status of stale bookings from one status to another.
     *
     * <p><b>JPA Feature:</b> {@code @Modifying} JPQL UPDATE — executes a single
     * SQL UPDATE statement that modifies multiple rows in one round-trip. This is
     * far more efficient than loading entities, changing fields, and flushing.</p>
     *
     * <p><b>Important:</b> Bulk operations bypass the persistence context. After this
     * method executes, any cached entities with the old status will be stale. The
     * calling service should either clear the persistence context or not rely on
     * cached entities for the affected rows.</p>
     *
     * @param oldStatus the current status to match
     * @param newStatus the new status to set
     * @param cutoff    only update bookings created before this timestamp
     * @return the number of rows updated
     */
    @Modifying
    @Query("UPDATE BookingJpaEntity b SET b.status = :newStatus, b.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE b.status = :oldStatus AND b.createdAt < :cutoff")
    int bulkUpdateStatus(
            @Param("oldStatus") String oldStatus,
            @Param("newStatus") String newStatus,
            @Param("cutoff") Instant cutoff);

    /**
     * Permanently deletes cancelled bookings older than the given cutoff.
     *
     * <p><b>JPA Feature:</b> {@code @Modifying} JPQL DELETE — hard-deletes rows
     * matching the criteria. In a production system, prefer soft-delete (status flag)
     * over hard-delete. This method is provided for housekeeping/archival purposes.</p>
     *
     * <p><b>Important:</b> Like bulk UPDATE, this bypasses the persistence context.
     * Deleted entities will remain in the first-level cache if previously loaded.</p>
     *
     * @param cutoff only delete bookings updated before this timestamp
     * @return the number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM BookingJpaEntity b WHERE b.status = 'CANCELLED' AND b.updatedAt < :cutoff")
    int purgeCancelledBookings(@Param("cutoff") Instant cutoff);

    // ==================== Pagination & Sorting ====================
    // Spring Data supports pagination via Pageable parameter and returns
    // Page (with total count) or Slice (without total count, for infinite scroll).

    /**
     * Finds bookings by status with full pagination support.
     *
     * <p><b>JPA Feature:</b> Derived query with {@link Page} return type — Spring Data
     * automatically generates two queries: one for the result page and one for the total
     * element count. The {@link Pageable} parameter controls page number, page size,
     * and sorting. The returned {@link Page} contains metadata about total pages,
     * total elements, and navigation (hasNext, hasPrevious).</p>
     *
     * <p><b>Performance note:</b> The count query can be expensive on large tables.
     * If you don't need the total count, use {@link Slice} instead (see below).</p>
     *
     * @param status   the booking status
     * @param pageable pagination and sorting parameters
     * @return a page of matching bookings with total count metadata
     */
    Page<BookingJpaEntity> findByStatus(String status, Pageable pageable);

    /**
     * Finds bookings by customer and status with {@link Slice}-based pagination.
     *
     * <p><b>JPA Feature:</b> Derived query with {@link Slice} return type — unlike
     * {@link Page}, a {@link Slice} does NOT execute a count query. It only checks
     * if there is a next slice by fetching {@code pageSize + 1} elements. This is
     * ideal for infinite-scroll UIs where the total count is not needed.</p>
     *
     * <p><b>When to use Slice vs Page:</b></p>
     * <ul>
     *   <li>{@link Page} — traditional pagination with "Page 1 of 42" display</li>
     *   <li>{@link Slice} — infinite scroll / "Load More" UIs, mobile apps</li>
     * </ul>
     *
     * @param customerId the customer UUID
     * @param status     the booking status
     * @param pageable   pagination parameters (page size determines fetch size)
     * @return a slice of matching bookings with hasNext indicator
     */
    Slice<BookingJpaEntity> findByCustomerIdAndStatus(UUID customerId, String status, Pageable pageable);

    /**
     * Finds bookings by status with explicit JPQL and pagination.
     *
     * <p><b>JPA Feature:</b> JPQL query combined with {@link Pageable} — demonstrates
     * that {@code @Query} methods also support pagination. Spring Data appends the
     * appropriate LIMIT/OFFSET clause based on the {@link Pageable} and generates
     * a count query for the {@link Page} wrapper.</p>
     *
     * <p>For native queries, you must provide a separate {@code countQuery} when
     * returning {@link Page}. For JPQL queries, the count query is derived automatically.</p>
     *
     * @param status   the booking status
     * @param pageable pagination and sorting parameters
     * @return a page of matching bookings
     */
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.status = :status")
    Page<BookingJpaEntity> findByStatusPaged(@Param("status") String status, Pageable pageable);

    // ==================== Stream Results (large datasets) ====================
    // Streaming avoids loading the entire result set into memory at once.
    // MUST be used within a read-only @Transactional method and closed after use.

    /**
     * Streams all bookings in a given status for memory-efficient processing.
     *
     * <p><b>JPA Feature:</b> {@link Stream} return type — instead of loading all
     * matching rows into a {@link List}, Spring Data returns a Java 8 {@link Stream}
     * backed by a scrolling database cursor. This is critical for processing
     * thousands or millions of rows without OutOfMemoryError.</p>
     *
     * <p><b>Usage requirements:</b></p>
     * <ul>
     *   <li>MUST be called within a {@code @Transactional(readOnly = true)} method</li>
     *   <li>The stream MUST be closed after use (try-with-resources recommended)</li>
     *   <li>The underlying JDBC cursor remains open while the stream is consumed</li>
     * </ul>
     *
     * <p>Example:</p>
     * <pre>{@code
     * @Transactional(readOnly = true)
     * public void processAllConfirmed() {
     *     try (Stream<BookingJpaEntity> stream = repository.streamByStatus("CONFIRMED")) {
     *         stream.map(mapper::toDomain)
     *               .forEach(this::process);
     *     }
     * }
     * }</pre>
     *
     * @param status the booking status to stream
     * @return a stream of matching booking entities (must be closed after use)
     */
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.status = :status")
    Stream<BookingJpaEntity> streamByStatus(@Param("status") String status);

    // ==================== @EntityGraph (N+1 prevention) ====================
    // EntityGraph controls eager/lazy fetching at the query level,
    // overriding the entity's default fetch strategy.

    /**
     * Finds all bookings for a customer with eager fetching of associated data.
     *
     * <p><b>JPA Feature:</b> {@code @EntityGraph} — overrides the default fetch
     * strategy defined on the entity for this specific query. The
     * {@code attributePaths} parameter specifies which associations should be
     * fetched eagerly (JOIN FETCH) regardless of their {@code @ManyToOne(fetch = LAZY)}
     * annotation on the entity.</p>
     *
     * <p><b>N+1 problem:</b> Without {@code @EntityGraph}, accessing lazy associations
     * in a loop would trigger one additional query per entity (N+1 queries total).
     * {@code @EntityGraph} pre-fetches the specified associations in a single query.</p>
     *
     * <p><b>Note:</b> In this entity, {@code voyageId} is a scalar UUID rather than
     * a {@code @ManyToOne} association. The {@code @EntityGraph} is demonstrated here
     * for reference — in a real scenario it would be applied to actual entity
     * associations (e.g., {@code @ManyToOne Voyage voyage}).</p>
     *
     * @param customerId the customer UUID
     * @return customer's bookings with eagerly fetched voyage data
     */
    @EntityGraph(attributePaths = {"voyageId"})
    @Query("SELECT b FROM BookingJpaEntity b WHERE b.customerId = :customerId")
    List<BookingJpaEntity> findByCustomerIdWithVoyageEager(@Param("customerId") UUID customerId);

    // ==================== DTO / Interface Projections ====================
    // Interface projections return proxy objects that only contain the selected
    // columns — significantly reducing data transfer and memory usage.

    /**
     * Finds lightweight booking summaries for a customer using interface projection.
     *
     * <p><b>JPA Feature:</b> Interface-based projection — the JPQL SELECT clause
     * aliases ({@code as bookingId}, {@code as status}, etc.) must match the getter
     * method names on the {@link BookingSummaryProjection} interface. Spring Data
     * creates a dynamic proxy at runtime that returns only the selected columns.</p>
     *
     * <p><b>Performance benefit:</b> Only 5 columns are transferred from the database
     * instead of all 18+ columns of the full entity. This is ideal for list views,
     * dashboards, and summary screens.</p>
     *
     * @param customerId the customer UUID
     * @return lightweight projection instances (not full entities)
     * @see BookingSummaryProjection
     */
    @Query("SELECT b.id as bookingId, b.status as status, b.originPort as originPort, "
            + "b.destinationPort as destinationPort, b.containerCount as containerCount "
            + "FROM BookingJpaEntity b WHERE b.customerId = :customerId")
    List<BookingSummaryProjection> findBookingSummariesByCustomerId(@Param("customerId") UUID customerId);

    // ==================== Named Queries (defined on entity) ====================
    // Named queries are defined on the entity class via @NamedQuery and referenced
    // here by convention: <EntityName>.<methodName>. They are validated at application
    // startup, catching JPQL syntax errors early.

    /**
     * Finds all non-terminal bookings (excludes CANCELLED and DELIVERED).
     *
     * <p><b>JPA Feature:</b> Named query — the actual JPQL is defined on
     * {@link BookingJpaEntity} via {@code @NamedQuery(name = "BookingJpaEntity.findActiveBookings")}.
     * Spring Data resolves the query by matching the method name to the named query name.
     * Named queries are pre-compiled and validated at application startup, providing
     * fail-fast behavior for JPQL syntax errors.</p>
     *
     * @return active bookings ordered by creation date descending
     * @see BookingJpaEntity
     */
    List<BookingJpaEntity> findActiveBookings();

    /**
     * Finds bookings for a specific route (origin → destination).
     *
     * <p><b>JPA Feature:</b> Named query with parameters — references
     * {@code @NamedQuery(name = "BookingJpaEntity.findByRoute")} defined on the entity.
     * Parameters are bound by name ({@code :origin}, {@code :destination}) matching
     * the method parameter names.</p>
     *
     * @param origin      the origin port code
     * @param destination the destination port code
     * @return bookings for the route, ordered by departure date
     * @see BookingJpaEntity
     */
    List<BookingJpaEntity> findByRoute(@Param("origin") String origin, @Param("destination") String destination);
}
