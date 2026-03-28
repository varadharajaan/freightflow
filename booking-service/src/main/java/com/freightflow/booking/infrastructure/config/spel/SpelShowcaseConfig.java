package com.freightflow.booking.infrastructure.config.spel;

import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Showcases Spring Expression Language (SpEL) across multiple Spring features.
 *
 * <h3>Spring Advanced Feature: SpEL (Spring Expression Language)</h3>
 * <p>SpEL is a powerful expression language used throughout the Spring framework.
 * It supports method invocation, string templating, collection selection/projection,
 * boolean logic, ternary operators, regex, type references, and bean references.</p>
 *
 * <h3>Where SpEL is Used in FreightFlow</h3>
 * <ul>
 *   <li><b>@Value</b>: Inject properties with defaults and transformations</li>
 *   <li><b>@Cacheable</b>: Dynamic cache key generation from method parameters</li>
 *   <li><b>@CacheEvict</b>: Conditional cache eviction based on return value</li>
 *   <li><b>@PreAuthorize</b>: Method-level security with role and data-based checks</li>
 *   <li><b>@ConditionalOnExpression</b>: Bean registration based on property expressions</li>
 *   <li><b>@Scheduled</b>: Dynamic cron expressions from properties</li>
 *   <li><b>@Query</b>: SpEL in Spring Data JPA queries (entity name injection)</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/core/expressions.html">SpEL Reference</a>
 */
@Configuration
public class SpelShowcaseConfig {

    private static final Logger log = LoggerFactory.getLogger(SpelShowcaseConfig.class);

    // ==================== @Value with SpEL ====================

    /**
     * SpEL in @Value — property injection with default value.
     * Expression: {@code ${property:default}}
     */
    @Value("${freightflow.booking.min-departure-days:7}")
    private int minDepartureDays;

    /**
     * SpEL — ternary operator in @Value.
     * If the environment variable ENVIRONMENT equals "prod", use 20; otherwise 5.
     */
    @Value("#{systemEnvironment['ENVIRONMENT'] == 'prod' ? 20 : 5}")
    private int maxConcurrentBookings;

    /**
     * SpEL — string manipulation in @Value.
     * Converts service name to uppercase for display purposes.
     */
    @Value("#{'${spring.application.name:freightflow}'.toUpperCase()}")
    private String serviceNameUpperCase;

    /**
     * SpEL — arithmetic in @Value.
     * Calculates max draft expiry from configured hours.
     */
    @Value("#{${freightflow.booking.draft-expiry-hours:72} * 60 * 60 * 1000}")
    private long draftExpiryMillis;

    /**
     * SpEL — referencing another bean's property.
     * Reads the partition count from FreightFlowProperties bean.
     */
    @Value("#{freightFlowProperties.kafka.partitions}")
    private int kafkaPartitions;

    @Bean
    public SpelDemoValues spelDemoValues() {
        log.info("SpEL resolved values: minDepartureDays={}, maxConcurrent={}, serviceName={}, draftExpiryMs={}, kafkaPartitions={}",
                minDepartureDays, maxConcurrentBookings, serviceNameUpperCase, draftExpiryMillis, kafkaPartitions);
        return new SpelDemoValues(minDepartureDays, maxConcurrentBookings, serviceNameUpperCase, draftExpiryMillis);
    }

    /**
     * Record holding SpEL-resolved values for inspection/testing.
     */
    public record SpelDemoValues(int minDepartureDays, int maxConcurrentBookings,
                                  String serviceNameUpperCase, long draftExpiryMillis) {}
}
