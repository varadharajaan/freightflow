package com.freightflow.booking.infrastructure.adapter.out.persistence.repository;

import java.util.UUID;

/**
 * Interface-based projection for lightweight booking summaries.
 *
 * <p>Spring Data JPA supports two types of projections for reading partial data from entities:</p>
 *
 * <h3>Interface-based projections (this approach)</h3>
 * <ul>
 *   <li>Spring Data dynamically creates proxy implementations at runtime</li>
 *   <li>Getter method names must match the alias or property name in the query</li>
 *   <li>Read-only — no setters, no mutation</li>
 *   <li>Minimal memory footprint — only requested columns are fetched from the database</li>
 *   <li>Can be nested (e.g., {@code getAddress().getCity()}) for associated entities</li>
 * </ul>
 *
 * <h3>Class-based projections (DTO projections)</h3>
 * <ul>
 *   <li>Uses a concrete class (often a record) with a matching constructor</li>
 *   <li>Constructor parameter names must match the JPQL aliases exactly</li>
 *   <li>Supports computed properties and custom logic in the class</li>
 *   <li>Slightly higher overhead than interface projections due to instantiation</li>
 * </ul>
 *
 * <p>Interface projections are preferred when you only need a read-only view of a subset
 * of entity fields. Class projections are better when you need custom logic or mutability.</p>
 *
 * <p>Used by
 * {@link SpringDataBookingRepository#findBookingSummariesByCustomerId(UUID)}
 * to return lightweight summaries without loading the entire entity graph.</p>
 *
 * @see SpringDataBookingRepository
 */
public interface BookingSummaryProjection {

    /**
     * Returns the booking's unique identifier.
     *
     * @return the booking UUID
     */
    UUID getBookingId();

    /**
     * Returns the current booking status as a string.
     *
     * @return the status name (e.g., "DRAFT", "CONFIRMED", "SHIPPED")
     */
    String getStatus();

    /**
     * Returns the UN/LOCODE of the origin port.
     *
     * @return the origin port code (e.g., "USLAX")
     */
    String getOriginPort();

    /**
     * Returns the UN/LOCODE of the destination port.
     *
     * @return the destination port code (e.g., "CNSHA")
     */
    String getDestinationPort();

    /**
     * Returns the number of containers booked.
     *
     * @return the container count
     */
    int getContainerCount();
}
