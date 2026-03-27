package com.freightflow.commons.observability.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Type-safe configuration properties for FreightFlow services.
 *
 * <h3>Spring Advanced Feature: @ConfigurationProperties</h3>
 * <p>Instead of scattered {@code @Value} annotations, this provides:</p>
 * <ul>
 *   <li><b>Type safety</b> — compile-time checked property binding</li>
 *   <li><b>Validation</b> — Jakarta Bean Validation on startup (fail-fast)</li>
 *   <li><b>IDE support</b> — auto-completion via {@code spring-configuration-metadata.json}</li>
 *   <li><b>Immutable</b> — Java 21 record-style with constructor binding</li>
 *   <li><b>Nested objects</b> — hierarchical configuration structure</li>
 * </ul>
 *
 * <p>Usage in application.yml:</p>
 * <pre>{@code
 * freightflow:
 *   service-name: booking-service
 *   profiling:
 *     sql:
 *       enabled: true
 *     slow-threshold: 1000ms
 *   booking:
 *     min-departure-days: 7
 *     max-containers-per-booking: 50
 *   kafka:
 *     partitions: 12
 *     replication-factor: 3
 * }</pre>
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Validated
@ConfigurationProperties(prefix = "freightflow")
public class FreightFlowProperties {

    @NotBlank
    private String serviceName = "freightflow";

    private ProfilingProperties profiling = new ProfilingProperties();
    private BookingProperties booking = new BookingProperties();
    private KafkaCustomProperties kafka = new KafkaCustomProperties();

    // ==================== Getters/Setters ====================

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public ProfilingProperties getProfiling() { return profiling; }
    public void setProfiling(ProfilingProperties profiling) { this.profiling = profiling; }

    public BookingProperties getBooking() { return booking; }
    public void setBooking(BookingProperties booking) { this.booking = booking; }

    public KafkaCustomProperties getKafka() { return kafka; }
    public void setKafka(KafkaCustomProperties kafka) { this.kafka = kafka; }

    // ==================== Nested Configuration Classes ====================

    /**
     * Profiling configuration — controls AOP profiling and SQL monitoring.
     */
    public static class ProfilingProperties {

        private SqlProfilingProperties sql = new SqlProfilingProperties();
        private Duration slowThreshold = Duration.ofMillis(1000);

        public SqlProfilingProperties getSql() { return sql; }
        public void setSql(SqlProfilingProperties sql) { this.sql = sql; }

        public Duration getSlowThreshold() { return slowThreshold; }
        public void setSlowThreshold(Duration slowThreshold) { this.slowThreshold = slowThreshold; }

        public static class SqlProfilingProperties {
            private boolean enabled = false;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }

    /**
     * Booking domain configuration — business rule parameters.
     */
    public static class BookingProperties {

        /** Minimum days before departure for a new booking. */
        @Min(1) @Max(90)
        private int minDepartureDays = 7;

        /** Maximum containers allowed in a single booking. */
        @Min(1) @Max(500)
        private int maxContainersPerBooking = 50;

        /** Maximum bookings a customer can have in DRAFT status. */
        @Min(1) @Max(100)
        private int maxDraftBookingsPerCustomer = 10;

        public int getMinDepartureDays() { return minDepartureDays; }
        public void setMinDepartureDays(int minDepartureDays) { this.minDepartureDays = minDepartureDays; }

        public int getMaxContainersPerBooking() { return maxContainersPerBooking; }
        public void setMaxContainersPerBooking(int maxContainersPerBooking) { this.maxContainersPerBooking = maxContainersPerBooking; }

        public int getMaxDraftBookingsPerCustomer() { return maxDraftBookingsPerCustomer; }
        public void setMaxDraftBookingsPerCustomer(int maxDraftBookingsPerCustomer) { this.maxDraftBookingsPerCustomer = maxDraftBookingsPerCustomer; }
    }

    /**
     * Custom Kafka configuration beyond Spring's default kafka properties.
     */
    public static class KafkaCustomProperties {

        @Min(1) @Max(256)
        private int partitions = 12;

        @Min(1) @Max(5)
        private int replicationFactor = 1;

        public int getPartitions() { return partitions; }
        public void setPartitions(int partitions) { this.partitions = partitions; }

        public int getReplicationFactor() { return replicationFactor; }
        public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }
    }
}
