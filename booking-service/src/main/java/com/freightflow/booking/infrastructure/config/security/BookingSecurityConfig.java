package com.freightflow.booking.infrastructure.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Booking-service-specific security configuration.
 *
 * <p>The base security filter chain and method-level security support are provided by the shared
 * {@code commons-security} auto-configuration. This class is reserved for booking-specific
 * extensions only (for example custom permission evaluators, voters, or interceptors).</p>
 *
 * <h3>Extension Points</h3>
 * <p>If the booking service needs custom security rules beyond what {@code commons-security}
 * provides (e.g., custom voters, permission evaluators), they should be registered as beans
 * in this configuration class.</p>
 *
 * @see com.freightflow.commons.security.config.FreightFlowSecurityAutoConfiguration
 */
@Configuration
public class BookingSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(BookingSecurityConfig.class);

    /**
     * Creates the booking security configuration.
     * <p>Logs activation for operational visibility during service startup.</p>
     */
    public BookingSecurityConfig() {
        log.info("Booking service security overrides loaded");
    }
}
