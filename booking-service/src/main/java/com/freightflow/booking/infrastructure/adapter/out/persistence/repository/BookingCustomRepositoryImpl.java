package com.freightflow.booking.infrastructure.adapter.out.persistence.repository;

import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.freightflow.commons.observability.profiling.Profiled;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link BookingCustomRepository} using the JPA Criteria API.
 *
 * <h3>Naming Convention</h3>
 * <p>This class MUST be named {@code BookingCustomRepositoryImpl} — the suffix {@code Impl}
 * is the Spring Data default for locating custom fragment implementations. The prefix must
 * match the fragment interface name exactly. This naming convention can be customized via
 * {@code @EnableJpaRepositories(repositoryImplementationPostfix = "...")} but the default
 * suffix is strongly recommended.</p>
 *
 * <h3>JPA Criteria API</h3>
 * <p>The Criteria API provides a type-safe, programmatic way to build queries at runtime.
 * Unlike JPQL strings, Criteria queries are checked at compile time (when using the JPA
 * metamodel) and can be dynamically composed based on runtime conditions.</p>
 *
 * <h3>Key components used</h3>
 * <ul>
 *   <li>{@link CriteriaBuilder} — factory for predicates, expressions, and queries</li>
 *   <li>{@link CriteriaQuery} — the query definition (SELECT, WHERE, ORDER BY, GROUP BY)</li>
 *   <li>{@link Root} — the FROM clause, representing the queried entity</li>
 *   <li>{@link Predicate} — WHERE clause conditions (AND, OR, EQUAL, BETWEEN, etc.)</li>
 * </ul>
 *
 * @see BookingCustomRepository
 * @see jakarta.persistence.criteria
 */
@Profiled(value = "BookingCustomRepositoryImpl", slowThresholdMs = 500)
public class BookingCustomRepositoryImpl implements BookingCustomRepository {

    private static final Logger log = LoggerFactory.getLogger(BookingCustomRepositoryImpl.class);

    private static final String FIELD_CUSTOMER_ID = "customerId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ORIGIN_PORT = "originPort";
    private static final String FIELD_DESTINATION_PORT = "destinationPort";
    private static final String FIELD_REQUESTED_DEPARTURE_DATE = "requestedDepartureDate";
    private static final String FIELD_CONTAINER_COUNT = "containerCount";
    private static final String FIELD_CONTAINER_TYPE = "containerType";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_WEIGHT_VALUE = "weightValue";

    private final EntityManager entityManager;

    /**
     * Constructs the custom repository with the required {@link EntityManager}.
     *
     * <p>Spring injects the same {@link EntityManager} used by the enclosing
     * {@link SpringDataBookingRepository} proxy, ensuring all queries participate
     * in the same persistence context and transaction.</p>
     *
     * @param entityManager the JPA entity manager
     */
    public BookingCustomRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation details:</p>
     * <ol>
     *   <li>Creates a {@link CriteriaQuery} for {@link BookingJpaEntity}</li>
     *   <li>Iterates over each optional filter in the criteria</li>
     *   <li>Only adds a {@link Predicate} for non-empty optionals</li>
     *   <li>Combines all predicates with {@link CriteriaBuilder#and(Predicate...)}</li>
     *   <li>Orders results by creation date descending</li>
     * </ol>
     */
    @Override
    @Profiled(value = "searchBookings", slowThresholdMs = 300)
    public List<BookingJpaEntity> searchBookings(BookingSearchCriteria criteria) {
        log.debug("Executing dynamic booking search with criteria: {}", criteria);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BookingJpaEntity> query = cb.createQuery(BookingJpaEntity.class);
        Root<BookingJpaEntity> root = query.from(BookingJpaEntity.class);

        List<Predicate> predicates = buildPredicates(criteria, cb, root);

        query.select(root)
                .where(cb.and(predicates.toArray(Predicate[]::new)))
                .orderBy(cb.desc(root.get(FIELD_CREATED_AT)));

        List<BookingJpaEntity> results = entityManager.createQuery(query).getResultList();
        log.debug("Dynamic booking search returned {} results", results.size());
        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation details:</p>
     * <ol>
     *   <li>Runs an aggregate query for total bookings, containers, weight, and average weight</li>
     *   <li>Runs a grouped query for per-status counts</li>
     *   <li>Combines results into an immutable {@link BookingStatistics} record</li>
     * </ol>
     *
     * <p>Uses {@link CriteriaBuilder#count}, {@link CriteriaBuilder#sum}, and
     * {@link CriteriaBuilder#avg} for server-side aggregation — no entities are
     * loaded into memory.</p>
     */
    @Override
    @Profiled(value = "calculateStatistics", slowThresholdMs = 500)
    public BookingStatistics calculateStatistics(UUID customerId) {
        log.debug("Calculating booking statistics for customerId={}", customerId);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Aggregate query: totals and averages
        CriteriaQuery<Tuple> aggregateQuery = cb.createTupleQuery();
        Root<BookingJpaEntity> aggRoot = aggregateQuery.from(BookingJpaEntity.class);

        aggregateQuery.multiselect(
                cb.count(aggRoot).alias("totalBookings"),
                cb.sum(aggRoot.get(FIELD_CONTAINER_COUNT)).alias("totalContainers"),
                cb.sum(aggRoot.get(FIELD_WEIGHT_VALUE)).alias("totalWeight"),
                cb.avg(aggRoot.get(FIELD_WEIGHT_VALUE)).alias("averageWeight")
        ).where(cb.equal(aggRoot.get(FIELD_CUSTOMER_ID), customerId));

        Tuple aggregateResult = entityManager.createQuery(aggregateQuery).getSingleResult();

        long totalBookings = extractLong(aggregateResult, 0);
        if (totalBookings == 0) {
            log.debug("No bookings found for customerId={}, returning empty statistics", customerId);
            return BookingStatistics.empty();
        }

        long totalContainers = extractLong(aggregateResult, 1);
        BigDecimal totalWeight = extractBigDecimal(aggregateResult, 2);
        BigDecimal averageWeight = extractBigDecimal(aggregateResult, 3);

        // Grouped query: count by status
        CriteriaQuery<Tuple> statusQuery = cb.createTupleQuery();
        Root<BookingJpaEntity> statusRoot = statusQuery.from(BookingJpaEntity.class);

        statusQuery.multiselect(
                statusRoot.get(FIELD_STATUS).alias("status"),
                cb.count(statusRoot).alias("count")
        ).where(cb.equal(statusRoot.get(FIELD_CUSTOMER_ID), customerId))
                .groupBy(statusRoot.get(FIELD_STATUS));

        List<Tuple> statusResults = entityManager.createQuery(statusQuery).getResultList();

        Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
        for (Tuple tuple : statusResults) {
            String status = tuple.get(0, String.class);
            Long count = tuple.get(1, Long.class);
            bookingsByStatus.put(status, count);
        }

        BookingStatistics statistics = new BookingStatistics(
                totalBookings, totalContainers, totalWeight, averageWeight, bookingsByStatus
        );

        log.debug("Calculated statistics for customerId={}: {}", customerId, statistics);
        return statistics;
    }

    // ==================== Private Helpers ====================

    /**
     * Builds a list of JPA predicates from the search criteria.
     *
     * <p>Each optional filter is independently checked; only non-empty values
     * contribute a predicate. This produces a dynamic WHERE clause that adapts
     * to whatever combination of filters the caller provides.</p>
     *
     * @param criteria the search criteria
     * @param cb       the criteria builder
     * @param root     the entity root
     * @return list of predicates (may be empty, which matches all rows)
     */
    private List<Predicate> buildPredicates(
            BookingSearchCriteria criteria,
            CriteriaBuilder cb,
            Root<BookingJpaEntity> root) {

        List<Predicate> predicates = new ArrayList<>();

        criteria.customerId().ifPresent(id -> {
            log.trace("Adding customerId filter: {}", id);
            predicates.add(cb.equal(root.get(FIELD_CUSTOMER_ID), id));
        });

        criteria.status().ifPresent(status -> {
            log.trace("Adding status filter: {}", status);
            predicates.add(cb.equal(root.get(FIELD_STATUS), status));
        });

        criteria.originPort().ifPresent(port -> {
            log.trace("Adding originPort filter: {}", port);
            predicates.add(cb.equal(root.get(FIELD_ORIGIN_PORT), port));
        });

        criteria.destinationPort().ifPresent(port -> {
            log.trace("Adding destinationPort filter: {}", port);
            predicates.add(cb.equal(root.get(FIELD_DESTINATION_PORT), port));
        });

        criteria.fromDate().ifPresent(from -> {
            log.trace("Adding fromDate filter: {}", from);
            predicates.add(cb.greaterThanOrEqualTo(root.get(FIELD_REQUESTED_DEPARTURE_DATE), from));
        });

        criteria.toDate().ifPresent(to -> {
            log.trace("Adding toDate filter: {}", to);
            predicates.add(cb.lessThanOrEqualTo(root.get(FIELD_REQUESTED_DEPARTURE_DATE), to));
        });

        criteria.minContainers().ifPresent(min -> {
            log.trace("Adding minContainers filter: {}", min);
            predicates.add(cb.greaterThanOrEqualTo(root.get(FIELD_CONTAINER_COUNT), min));
        });

        criteria.containerType().ifPresent(type -> {
            log.trace("Adding containerType filter: {}", type);
            predicates.add(cb.equal(root.get(FIELD_CONTAINER_TYPE), type));
        });

        return predicates;
    }

    /**
     * Safely extracts a long value from a Criteria API tuple result.
     *
     * @param tuple the result tuple
     * @param index the column index
     * @return the long value, or 0 if null
     */
    private long extractLong(Tuple tuple, int index) {
        Number value = tuple.get(index, Number.class);
        return value != null ? value.longValue() : 0L;
    }

    /**
     * Safely extracts a {@link BigDecimal} value from a Criteria API tuple result.
     *
     * @param tuple the result tuple
     * @param index the column index
     * @return the BigDecimal value, or {@link BigDecimal#ZERO} if null
     */
    private BigDecimal extractBigDecimal(Tuple tuple, int index) {
        Number value = tuple.get(index, Number.class);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return BigDecimal.valueOf(value.doubleValue());
    }
}
