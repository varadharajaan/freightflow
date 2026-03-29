package com.freightflow.booking.infrastructure.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Booking-service-specific security configuration.
 *
 * <p>Enables method-level security annotations ({@code @PreAuthorize}, {@code @PostAuthorize},
 * etc.) for fine-grained access control on service and controller methods. The base security
 * filter chain (JWT validation, CORS, headers) is provided by the shared
 * {@code commons-security} auto-configuration.</p>
 *
 * <h3>Method Security in the Booking Service</h3>
 * <p>The booking service uses {@code @PreAuthorize} annotations on controller methods to
 * enforce role-based access control:</p>
 * <ul>
 *   <li><b>Create booking</b> — ADMIN, OPERATOR, CUSTOMER</li>
 *   <li><b>View booking</b> — ADMIN, OPERATOR, CUSTOMER</li>
 *   <li><b>Confirm booking</b> — ADMIN, OPERATOR only (customers cannot confirm)</li>
 *   <li><b>Cancel booking</b> — ADMIN, OPERATOR, CUSTOMER</li>
 *   <li><b>List by customer</b> — ADMIN, OPERATOR, or the customer themselves</li>
 * </ul>
 *
 * <h3>Extension Points</h3>
 * <p>If the booking service needs custom security rules beyond what {@code commons-security}
 * provides (e.g., custom voters, permission evaluators), they should be registered as beans
 * in this configuration class.</p>
 *
 * @see com.freightflow.commons.security.config.FreightFlowSecurityAutoConfiguration
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class BookingSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(BookingSecurityConfig.class);

    /**
     * Creates the booking security configuration.
     * <p>Logs activation for operational visibility during service startup.</p>
     */
    public BookingSecurityConfig() {
        log.info("Booking service method-level security enabled — @PreAuthorize annotations active");
    }
}
