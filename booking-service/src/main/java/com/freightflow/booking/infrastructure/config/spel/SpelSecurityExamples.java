package com.freightflow.booking.infrastructure.config.spel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Demonstrates SpEL expressions in Spring Security method-level authorization.
 *
 * <h3>SpEL in Spring Security</h3>
 * <p>Spring Security's method-level annotations ({@code @PreAuthorize}, {@code @PostAuthorize},
 * {@code @PreFilter}, {@code @PostFilter}) use SpEL to define access control rules.
 * These evaluate at runtime against the current {@code Authentication} object.</p>
 *
 * <h3>Available SpEL Variables in Security Context</h3>
 * <ul>
 *   <li>{@code authentication} — the full Authentication object</li>
 *   <li>{@code principal} — the principal (usually UserDetails)</li>
 *   <li>{@code hasRole('ROLE_ADMIN')} — checks GrantedAuthority</li>
 *   <li>{@code hasAuthority('booking:write')} — checks specific authority</li>
 *   <li>{@code #paramName} — access method parameters by name</li>
 *   <li>{@code returnObject} — access return value (in @PostAuthorize)</li>
 *   <li>{@code filterObject} — current element in collection filtering</li>
 * </ul>
 *
 * <p>These examples are documentation/showcase code. Actual security will be
 * integrated in Topic T9 (Security - OAuth2/OIDC with Keycloak).</p>
 *
 * @see org.springframework.security.access.prepost.PreAuthorize
 */
@Service
public class SpelSecurityExamples {

    private static final Logger log = LoggerFactory.getLogger(SpelSecurityExamples.class);

    /**
     * SpEL in @PreAuthorize — role-based access control.
     *
     * <p>Only users with ADMIN or OPERATOR roles can access this method.
     * SpEL: {@code hasAnyRole('ADMIN', 'OPERATOR')}</p>
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public void adminOnlyOperation() {
        log.info("Admin/operator operation executed");
    }

    /**
     * SpEL in @PreAuthorize — authority-based access with parameter check.
     *
     * <p>Checks BOTH a fine-grained authority AND that the user is accessing their own data.
     * SpEL: {@code hasAuthority('booking:read') and #customerId == authentication.name}</p>
     *
     * <p>This prevents a customer from reading another customer's bookings — even if they
     * have the 'booking:read' authority. This is data-level authorization.</p>
     */
    @PreAuthorize("hasAuthority('booking:read') and (#customerId == authentication.name or hasRole('ADMIN'))")
    public Object getBookingsForCustomer(String customerId) {
        log.debug("Getting bookings for customer: customerId={}", customerId);
        return null;
    }

    /**
     * SpEL in @PreAuthorize — complex boolean expression.
     *
     * <p>Allows access if: user is ADMIN, OR (user is OPERATOR AND booking is not CANCELLED).
     * Shows compound boolean logic in SpEL security expressions.</p>
     */
    @PreAuthorize("hasRole('ADMIN') or (hasRole('OPERATOR') and #status != 'CANCELLED')")
    public void conditionalAccess(String bookingId, String status) {
        log.debug("Conditional access granted: bookingId={}, status={}", bookingId, status);
    }

    /**
     * SpEL in @PostAuthorize — checks AFTER method execution.
     *
     * <p>The method runs first, then SpEL evaluates against the return value.
     * If the check fails, the result is NOT returned (throws AccessDeniedException).
     * Useful when the authorization depends on the returned data.</p>
     *
     * <p>SpEL: {@code returnObject.customerId() == authentication.name or hasRole('ADMIN')} —
     * ensures users can only see their own bookings.</p>
     */
    @PostAuthorize("returnObject.customerId() == authentication.name or hasRole('ADMIN')")
    public BookingSecurityView getBookingSecure(String bookingId) {
        log.debug("Post-authorize check on booking: bookingId={}", bookingId);
        return new BookingSecurityView(bookingId, "customer-123", "CONFIRMED");
    }

    /**
     * SpEL in @PreFilter — filters input collection BEFORE method execution.
     *
     * <p>Removes elements from the input list that don't match the SpEL condition.
     * {@code filterObject} refers to the current element being evaluated.</p>
     *
     * <p>SpEL: {@code filterObject.status() != 'CANCELLED'} — removes cancelled bookings
     * from the input before processing. Useful for batch operations.</p>
     */
    @PreFilter("filterObject.status() != 'CANCELLED'")
    public void processBatchBookings(List<BookingSecurityView> bookings) {
        log.info("Processing {} bookings after PreFilter", bookings.size());
    }

    /**
     * SpEL in @PostFilter — filters output collection AFTER method execution.
     *
     * <p>Removes elements from the returned list that the user shouldn't see.
     * {@code filterObject} refers to each element in the return value.</p>
     *
     * <p>SpEL: {@code filterObject.customerId() == authentication.name or hasRole('ADMIN')} —
     * non-admin users only see their own bookings in the result.</p>
     */
    @PostFilter("filterObject.customerId() == authentication.name or hasRole('ADMIN')")
    public List<BookingSecurityView> getAllBookingsFiltered() {
        log.debug("Returning all bookings — PostFilter will remove unauthorized entries");
        return List.of(
                new BookingSecurityView("BKG-001", "customer-A", "CONFIRMED"),
                new BookingSecurityView("BKG-002", "customer-B", "SHIPPED"),
                new BookingSecurityView("BKG-003", "customer-A", "DRAFT")
        );
    }

    /**
     * Simplified booking view for security SpEL demonstrations.
     */
    public record BookingSecurityView(String bookingId, String customerId, String status) {}
}
