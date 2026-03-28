package com.freightflow.booking.infrastructure.config;

import com.freightflow.booking.infrastructure.adapter.out.persistence.repository.SpringDataBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * Custom Spring Boot Actuator health indicator for the booking service.
 *
 * <h3>Custom Health Indicators</h3>
 * <p>Spring Boot Actuator provides the {@code /actuator/health} endpoint which aggregates
 * health status from multiple {@link org.springframework.boot.actuate.health.HealthIndicator}
 * beans. Custom health indicators allow services to report on domain-specific health
 * aspects beyond the built-in checks (database, disk space, etc.).</p>
 *
 * <h3>Implementation approaches</h3>
 * <ul>
 *   <li>{@link org.springframework.boot.actuate.health.HealthIndicator} — functional
 *       interface with a single {@code health()} method</li>
 *   <li>{@link AbstractHealthIndicator} (this approach) — provides built-in exception
 *       handling: if {@link #doHealthCheck(Health.Builder)} throws, the indicator
 *       automatically reports {@code DOWN} with the exception details</li>
 * </ul>
 *
 * <h3>Health status semantics</h3>
 * <ul>
 *   <li>{@code UP} — the component is functioning normally</li>
 *   <li>{@code DOWN} — the component has failed and the service cannot function</li>
 *   <li>{@code OUT_OF_SERVICE} — the component is intentionally taken offline</li>
 *   <li>{@code UNKNOWN} — the health cannot be determined</li>
 * </ul>
 *
 * <h3>Endpoint exposure</h3>
 * <p>This indicator appears under {@code /actuator/health} (or its detailed view
 * {@code /actuator/health/booking} if {@code management.endpoint.health.show-details}
 * is set to {@code always} or {@code when-authorized}).</p>
 *
 * @see AbstractHealthIndicator
 * @see org.springframework.boot.actuate.health.Health
 */
@Component
public class BookingHealthIndicator extends AbstractHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(BookingHealthIndicator.class);

    private static final String DETAIL_BOOKING_COUNT = "bookingCount";
    private static final String DETAIL_DATABASE = "database";
    private static final String DETAIL_KAFKA = "kafka";
    private static final String DETAIL_STATUS_CONNECTED = "connected";
    private static final String DETAIL_STATUS_ASSUMED_OK = "assumed_ok (placeholder)";

    private final SpringDataBookingRepository bookingRepository;

    /**
     * Constructs the health indicator with required dependencies.
     *
     * @param bookingRepository the booking repository for database connectivity checks
     */
    public BookingHealthIndicator(SpringDataBookingRepository bookingRepository) {
        super("Booking service health check failed");
        this.bookingRepository = bookingRepository;
    }

    /**
     * Performs the actual health check.
     *
     * <p>Checks the following aspects:</p>
     * <ol>
     *   <li><b>Database connectivity</b> — executes a lightweight {@code COUNT(*)} query
     *       via the booking repository. If the query succeeds, the database is reachable
     *       and the bookings table exists.</li>
     *   <li><b>Kafka connectivity</b> — placeholder check. In production, this would
     *       verify that the Kafka producer can reach the broker (e.g., via
     *       {@code AdminClient.describeCluster()}).</li>
     * </ol>
     *
     * <p>If any check fails with an exception, {@link AbstractHealthIndicator}
     * automatically catches it and reports {@code DOWN} with the exception message.</p>
     *
     * @param builder the health builder to populate with status and details
     * @throws Exception if any health check fails (caught by AbstractHealthIndicator)
     */
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        log.debug("Performing booking service health check");

        // Check 1: Database connectivity via repository count query
        long bookingCount = checkDatabaseConnectivity();

        // Check 2: Kafka connectivity (placeholder)
        String kafkaStatus = checkKafkaConnectivity();

        builder.up()
                .withDetail(DETAIL_BOOKING_COUNT, bookingCount)
                .withDetail(DETAIL_DATABASE, DETAIL_STATUS_CONNECTED)
                .withDetail(DETAIL_KAFKA, kafkaStatus);

        log.debug("Booking service health check passed: bookingCount={}", bookingCount);
    }

    /**
     * Verifies database connectivity by executing a lightweight count query.
     *
     * <p>Using {@code count()} is preferred over {@code findAll()} because it
     * returns a single scalar value without loading entity data into memory.</p>
     *
     * @return the total booking count
     */
    private long checkDatabaseConnectivity() {
        long count = bookingRepository.count();
        log.trace("Database health check: {} bookings found", count);
        return count;
    }

    /**
     * Placeholder for Kafka broker connectivity check.
     *
     * <p>In a production implementation, this would use
     * {@code org.apache.kafka.clients.admin.AdminClient} to verify cluster
     * availability and topic existence.</p>
     *
     * @return the Kafka connectivity status string
     */
    private String checkKafkaConnectivity() {
        // TODO: Implement actual Kafka health check using AdminClient
        return DETAIL_STATUS_ASSUMED_OK;
    }
}
