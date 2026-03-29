package com.freightflow.commons.security.rbac;

/**
 * Centralized RBAC role constants for the FreightFlow platform.
 *
 * <p>Defines all role names used across the platform, ensuring consistency between
 * Keycloak realm configuration, Spring Security annotations, and SpEL expressions.
 * All FreightFlow services should reference these constants instead of hard-coding
 * role strings.</p>
 *
 * <h3>Role Hierarchy</h3>
 * <ul>
 *   <li><b>ADMIN</b> — Full system access. Can manage all entities, users, and system
 *       configuration. Typically assigned to platform administrators.</li>
 *   <li><b>OPERATOR</b> — Operational access. Can manage bookings, vessels, tracking,
 *       and day-to-day logistics. Cannot manage users or system configuration.</li>
 *   <li><b>CUSTOMER</b> — Customer-facing access. Can view and manage own bookings,
 *       view vessel schedules. Cannot access other customers' data.</li>
 *   <li><b>FINANCE</b> — Financial access. Can manage billing, invoices, and payments.
 *       Can view bookings for billing purposes but cannot modify them.</li>
 * </ul>
 *
 * <h3>Usage in {@code @PreAuthorize} Annotations</h3>
 * <pre>{@code
 * @PreAuthorize(Roles.HAS_ADMIN)
 * public void deleteUser(String userId) { ... }
 *
 * @PreAuthorize(Roles.HAS_ADMIN_OR_OPERATOR)
 * public Booking confirmBooking(String bookingId) { ... }
 * }</pre>
 *
 * <h3>Usage in Programmatic Checks</h3>
 * <pre>{@code
 * if (CurrentUser.getCurrentRoles().contains(Roles.ADMIN)) {
 *     // admin-only logic
 * }
 * }</pre>
 *
 * @see CurrentUser
 */
public final class Roles {

    private Roles() {
        throw new AssertionError("Constants class — do not instantiate");
    }

    // ==================== Role Name Constants ====================

    /** Full system access — platform administrators. */
    public static final String ADMIN = "ROLE_ADMIN";

    /** Operational access — logistics managers and operators. */
    public static final String OPERATOR = "ROLE_OPERATOR";

    /** Customer access — shippers and consignees. */
    public static final String CUSTOMER = "ROLE_CUSTOMER";

    /** Finance access — billing and payments team. */
    public static final String FINANCE = "ROLE_FINANCE";

    // ==================== SpEL Expressions for @PreAuthorize ====================

    /**
     * SpEL: user must have the ADMIN role.
     * <p>Usage: {@code @PreAuthorize(Roles.HAS_ADMIN)}</p>
     */
    public static final String HAS_ADMIN = "hasRole('ADMIN')";

    /**
     * SpEL: user must have the OPERATOR role.
     * <p>Usage: {@code @PreAuthorize(Roles.HAS_OPERATOR)}</p>
     */
    public static final String HAS_OPERATOR = "hasRole('OPERATOR')";

    /**
     * SpEL: user must have the CUSTOMER role.
     * <p>Usage: {@code @PreAuthorize(Roles.HAS_CUSTOMER)}</p>
     */
    public static final String HAS_CUSTOMER = "hasRole('CUSTOMER')";

    /**
     * SpEL: user must have the FINANCE role.
     * <p>Usage: {@code @PreAuthorize(Roles.HAS_FINANCE)}</p>
     */
    public static final String HAS_FINANCE = "hasRole('FINANCE')";

    /**
     * SpEL: user must have ADMIN or OPERATOR role.
     * <p>Usage: {@code @PreAuthorize(Roles.HAS_ADMIN_OR_OPERATOR)}</p>
     */
    public static final String HAS_ADMIN_OR_OPERATOR = "hasAnyRole('ADMIN', 'OPERATOR')";

    /**
     * SpEL: user must have ADMIN, OPERATOR, or CUSTOMER role (any authenticated business user).
     * <p>Usage: {@code @PreAuthorize(Roles.HAS_ADMIN_OR_OPERATOR_OR_CUSTOMER)}</p>
     */
    public static final String HAS_ADMIN_OR_OPERATOR_OR_CUSTOMER =
            "hasAnyRole('ADMIN', 'OPERATOR', 'CUSTOMER')";

    /**
     * SpEL: user must have ADMIN or FINANCE role.
     * <p>Usage: {@code @PreAuthorize(Roles.HAS_ADMIN_OR_FINANCE)}</p>
     */
    public static final String HAS_ADMIN_OR_FINANCE = "hasAnyRole('ADMIN', 'FINANCE')";

    /**
     * SpEL: user must have any of the four defined roles (any authenticated platform user).
     * <p>Usage: {@code @PreAuthorize(Roles.HAS_ANY_ROLE)}</p>
     */
    public static final String HAS_ANY_ROLE =
            "hasAnyRole('ADMIN', 'OPERATOR', 'CUSTOMER', 'FINANCE')";
}
