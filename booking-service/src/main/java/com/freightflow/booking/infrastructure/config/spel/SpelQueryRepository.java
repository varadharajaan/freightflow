package com.freightflow.booking.infrastructure.config.spel;

import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Demonstrates SpEL expressions within Spring Data JPA {@code @Query} annotations.
 *
 * <h3>SpEL in @Query</h3>
 * <p>Spring Data JPA supports SpEL in JPQL queries via the {@code #{}} syntax.
 * The most common use is entity name injection via {@code #{#entityName}},
 * which allows writing generic queries that work across entity types.</p>
 *
 * <h3>Available SpEL Variables in @Query</h3>
 * <ul>
 *   <li>{@code #{#entityName}} — the entity name (e.g., "BookingJpaEntity")</li>
 *   <li>{@code :#{principal.username}} — current authenticated user (Spring Security)</li>
 *   <li>{@code :#{#paramObj.field}} — nested property access on parameters</li>
 *   <li>{@code ?#{[0]}} — positional parameter reference</li>
 * </ul>
 *
 * <p>This is a supplementary repository interface showing SpEL-specific query patterns.
 * The main repository is {@link com.freightflow.booking.infrastructure.adapter.out.persistence.repository.SpringDataBookingRepository}.</p>
 *
 * @see <a href="https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.query.spel-expressions">SpEL in Spring Data JPA</a>
 */
public interface SpelQueryRepository extends JpaRepository<BookingJpaEntity, UUID> {

    /**
     * SpEL — {@code #{#entityName}} for generic entity name injection.
     *
     * <p>The {@code #{#entityName}} expression is replaced with the entity's name at runtime.
     * This is useful for generic/reusable queries, especially in abstract base repositories.</p>
     *
     * <p>Generated JPQL: {@code SELECT b FROM BookingJpaEntity b WHERE b.status = ?1}</p>
     */
    @Query("SELECT b FROM #{#entityName} b WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<BookingJpaEntity> findAllByStatusUsingSpel(@Param("status") String status);

    /**
     * SpEL — nested property access on parameter object.
     *
     * <p>Accesses nested fields from a parameter object using SpEL dot notation.
     * The parameter {@code criteria} is a record/POJO with fields {@code origin} and {@code destination}.</p>
     *
     * <p>This avoids having to decompose the object into individual parameters.</p>
     */
    @Query("SELECT b FROM #{#entityName} b WHERE b.originPort = :#{#criteria.origin} AND b.destinationPort = :#{#criteria.destination}")
    List<BookingJpaEntity> findByRouteCriteria(@Param("criteria") RouteCriteria criteria);

    /**
     * SpEL — conditional null handling with ternary operator.
     *
     * <p>If the status parameter is null, match all statuses (using {@code IS NOT NULL}).
     * If status is provided, filter by that specific status.</p>
     *
     * <p>This pattern allows optional filtering without multiple query methods.</p>
     */
    @Query("SELECT b FROM #{#entityName} b WHERE (:#{#status == null} = true OR b.status = :status)")
    List<BookingJpaEntity> findWithOptionalStatusFilter(@Param("status") String status);

    /**
     * SpEL — collection parameter with SpEL transformation.
     *
     * <p>Uses SpEL to access collection size for validation within the query context.</p>
     */
    @Query("SELECT b FROM #{#entityName} b WHERE b.originPort IN :ports ORDER BY b.createdAt DESC")
    List<BookingJpaEntity> findByOriginPortsSpel(@Param("ports") List<String> ports);

    /**
     * Route criteria record for SpEL nested property access in @Query.
     *
     * @param origin      the origin port code
     * @param destination the destination port code
     */
    record RouteCriteria(String origin, String destination) {}
}
