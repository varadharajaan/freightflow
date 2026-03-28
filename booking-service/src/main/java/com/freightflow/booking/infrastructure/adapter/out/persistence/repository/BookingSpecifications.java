package com.freightflow.booking.infrastructure.adapter.out.persistence.repository;

import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Composable JPA Specifications for building dynamic booking queries.
 *
 * <h3>Spring Advanced Feature: JPA Specifications (Specification Pattern)</h3>
 * <p>Specifications encapsulate individual query predicates as reusable, composable
 * objects. They can be combined using {@code .and()}, {@code .or()}, and {@code .not()}
 * to build complex queries dynamically at runtime — without writing JPQL or native SQL.</p>
 *
 * <h3>Why Specifications over Derived Query Methods?</h3>
 * <ul>
 *   <li><b>Derived queries</b>: fixed at compile time — {@code findByStatusAndCustomerId()}</li>
 *   <li><b>Specifications</b>: composed at runtime — add/remove filters dynamically based on user input</li>
 *   <li>Avoids method name explosion: instead of 20+ derived methods, compose 5 specifications</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * Specification<BookingJpaEntity> spec = BookingSpecifications.hasStatus("CONFIRMED")
 *     .and(BookingSpecifications.forCustomer(customerId))
 *     .and(BookingSpecifications.departsBetween(from, to))
 *     .and(BookingSpecifications.onRoute("DEHAM", "CNSHA"));
 *
 * Page<BookingJpaEntity> results = repository.findAll(spec, PageRequest.of(0, 20));
 * }</pre>
 *
 * <h3>N+1 Prevention</h3>
 * <p>Specifications can be combined with {@code @EntityGraph} or used with
 * {@code repository.findAll(spec)} which generates a single SQL query with
 * all predicates in the WHERE clause — no N+1 problem.</p>
 *
 * @see org.springframework.data.jpa.domain.Specification
 * @see org.springframework.data.jpa.repository.JpaSpecificationExecutor
 */
public final class BookingSpecifications {

    private BookingSpecifications() {
        throw new AssertionError("Specification factory — do not instantiate");
    }

    /**
     * Filters bookings by status.
     *
     * <p>SQL: {@code WHERE status = ?}</p>
     *
     * @param status the booking status to match
     * @return a specification filtering by status
     */
    public static Specification<BookingJpaEntity> hasStatus(String status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Filters bookings by customer ID.
     *
     * <p>SQL: {@code WHERE customer_id = ?}</p>
     *
     * @param customerId the customer UUID
     * @return a specification filtering by customer
     */
    public static Specification<BookingJpaEntity> forCustomer(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    /**
     * Filters bookings by origin port.
     *
     * <p>SQL: {@code WHERE origin_port = ?}</p>
     *
     * @param portCode the UN/LOCODE port code
     * @return a specification filtering by origin
     */
    public static Specification<BookingJpaEntity> fromPort(String portCode) {
        return (root, query, cb) -> cb.equal(root.get("originPort"), portCode);
    }

    /**
     * Filters bookings by destination port.
     *
     * <p>SQL: {@code WHERE destination_port = ?}</p>
     *
     * @param portCode the UN/LOCODE port code
     * @return a specification filtering by destination
     */
    public static Specification<BookingJpaEntity> toPort(String portCode) {
        return (root, query, cb) -> cb.equal(root.get("destinationPort"), portCode);
    }

    /**
     * Filters bookings on a specific route (origin AND destination).
     *
     * <p>SQL: {@code WHERE origin_port = ? AND destination_port = ?}</p>
     * <p>Demonstrates Specification composition using {@code .and()}.</p>
     *
     * @param origin      the origin port code
     * @param destination the destination port code
     * @return a composed specification
     */
    public static Specification<BookingJpaEntity> onRoute(String origin, String destination) {
        return fromPort(origin).and(toPort(destination));
    }

    /**
     * Filters bookings with departure date in a range (inclusive).
     *
     * <p>SQL: {@code WHERE requested_departure_date BETWEEN ? AND ?}</p>
     *
     * @param from start date (inclusive)
     * @param to   end date (inclusive)
     * @return a specification filtering by departure date range
     */
    public static Specification<BookingJpaEntity> departsBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.between(root.get("requestedDepartureDate"), from, to);
    }

    /**
     * Filters bookings with departure date after a given date.
     *
     * <p>SQL: {@code WHERE requested_departure_date > ?}</p>
     *
     * @param date the minimum departure date (exclusive)
     * @return a specification
     */
    public static Specification<BookingJpaEntity> departsAfter(LocalDate date) {
        return (root, query, cb) -> cb.greaterThan(root.get("requestedDepartureDate"), date);
    }

    /**
     * Filters bookings with minimum container count.
     *
     * <p>SQL: {@code WHERE container_count >= ?}</p>
     *
     * @param minContainers minimum number of containers
     * @return a specification
     */
    public static Specification<BookingJpaEntity> hasMinContainers(int minContainers) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("containerCount"), minContainers);
    }

    /**
     * Filters bookings by container type.
     *
     * <p>SQL: {@code WHERE container_type = ?}</p>
     *
     * @param containerType the container type
     * @return a specification
     */
    public static Specification<BookingJpaEntity> hasContainerType(String containerType) {
        return (root, query, cb) -> cb.equal(root.get("containerType"), containerType);
    }

    /**
     * Filters bookings by voyage assignment.
     *
     * <p>SQL: {@code WHERE voyage_id = ?}</p>
     *
     * @param voyageId the voyage UUID
     * @return a specification
     */
    public static Specification<BookingJpaEntity> assignedToVoyage(UUID voyageId) {
        return (root, query, cb) -> cb.equal(root.get("voyageId"), voyageId);
    }

    /**
     * Filters bookings that have NOT been assigned to a voyage.
     *
     * <p>SQL: {@code WHERE voyage_id IS NULL}</p>
     *
     * @return a specification for unassigned bookings
     */
    public static Specification<BookingJpaEntity> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("voyageId"));
    }

    /**
     * Filters bookings created after a specific timestamp.
     *
     * <p>SQL: {@code WHERE created_at > ?}</p>
     *
     * @param after the cutoff timestamp
     * @return a specification
     */
    public static Specification<BookingJpaEntity> createdAfter(Instant after) {
        return (root, query, cb) -> cb.greaterThan(root.get("createdAt"), after);
    }

    /**
     * Filters bookings in any of the given statuses.
     *
     * <p>SQL: {@code WHERE status IN (?, ?, ?)}</p>
     *
     * @param statuses the list of statuses to match
     * @return a specification using IN clause
     */
    public static Specification<BookingJpaEntity> statusIn(List<String> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /**
     * Filters bookings NOT in a specific status.
     *
     * <p>SQL: {@code WHERE status != ?}</p>
     * <p>Demonstrates Specification negation using {@code Specification.not()}.</p>
     *
     * @param status the status to exclude
     * @return a negated specification
     */
    public static Specification<BookingJpaEntity> statusNot(String status) {
        return Specification.not(hasStatus(status));
    }

    /**
     * Filters bookings where cargo description contains a search term (case-insensitive).
     *
     * <p>SQL: {@code WHERE LOWER(description) LIKE '%term%'}</p>
     *
     * @param searchTerm the text to search for
     * @return a specification with LIKE clause
     */
    public static Specification<BookingJpaEntity> descriptionContains(String searchTerm) {
        return (root, query, cb) -> cb.like(
                cb.lower(root.get("description")),
                "%" + searchTerm.toLowerCase() + "%"
        );
    }

    /**
     * Orders results by creation date descending.
     *
     * <p>Demonstrates adding ORDER BY via Specification.
     * Note: For simple ordering, prefer {@code Sort} parameter on repository methods.</p>
     *
     * @return a specification that adds ORDER BY created_at DESC
     */
    public static Specification<BookingJpaEntity> orderedByCreatedAtDesc() {
        return (root, query, cb) -> {
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.conjunction(); // no WHERE clause, just ordering
        };
    }
}
