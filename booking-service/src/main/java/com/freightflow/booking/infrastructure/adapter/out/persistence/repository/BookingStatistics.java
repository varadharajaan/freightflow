package com.freightflow.booking.infrastructure.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Immutable record holding aggregated booking statistics for a customer.
 *
 * <p>This record is produced by
 * {@link BookingCustomRepository#calculateStatistics(java.util.UUID)}
 * using JPA Criteria API aggregate functions (COUNT, SUM, AVG). It avoids
 * transferring raw entity data over the wire when only summary metrics are needed.</p>
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>Uses a Java 21 record for automatic immutability, equals/hashCode, and toString</li>
 *   <li>The {@code bookingsByStatus} map provides a status-wise breakdown</li>
 *   <li>All monetary and weight totals use {@link BigDecimal} to avoid floating-point errors</li>
 * </ul>
 *
 * @param totalBookings    the total number of bookings for the customer
 * @param totalContainers  the sum of all container counts across bookings
 * @param totalWeight      the sum of all weight values across bookings
 * @param averageWeight    the average weight per booking
 * @param bookingsByStatus a map of status name to count (e.g., {"DRAFT": 3, "CONFIRMED": 7})
 *
 * @see BookingCustomRepository
 * @see BookingCustomRepositoryImpl
 */
public record BookingStatistics(
        long totalBookings,
        long totalContainers,
        BigDecimal totalWeight,
        BigDecimal averageWeight,
        Map<String, Long> bookingsByStatus
) {

    /**
     * Returns a statistics instance representing zero bookings.
     *
     * @return empty statistics with all values at zero
     */
    public static BookingStatistics empty() {
        return new BookingStatistics(0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
    }
}
