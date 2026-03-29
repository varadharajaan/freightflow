package com.freightflow.commons.security.rbac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for extracting current user information from the Spring Security context.
 *
 * <p>Provides convenient static methods to access the authenticated user's identity
 * and roles without directly interacting with {@link SecurityContextHolder}. All methods
 * are null-safe and return {@link Optional} where the value may not be present (e.g.,
 * unauthenticated requests, missing JWT claims).</p>
 *
 * <h3>Usage Examples</h3>
 * <pre>{@code
 * // Get the current user's ID (JWT subject)
 * String userId = CurrentUser.getCurrentUserId()
 *     .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
 *
 * // Check if the current user is an admin
 * if (CurrentUser.isAdmin()) {
 *     // admin-only logic
 * }
 *
 * // Get all roles for logging
 * Set<String> roles = CurrentUser.getCurrentRoles();
 * log.info("User {} has roles: {}", CurrentUser.getCurrentUsername().orElse("anonymous"), roles);
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>All methods delegate to {@link SecurityContextHolder}, which uses a {@code ThreadLocal}
 * strategy by default. This is safe for servlet-based applications where each request runs
 * on its own thread. For virtual threads (Java 21), Spring Boot auto-configures the
 * {@code InheritableThreadLocal} strategy.</p>
 *
 * @see Roles
 * @see SecurityContextHolder
 */
public final class CurrentUser {

    private static final Logger log = LoggerFactory.getLogger(CurrentUser.class);

    private CurrentUser() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Returns the unique identifier (JWT {@code sub} claim) of the currently authenticated user.
     *
     * <p>For Keycloak JWTs, this is typically a UUID assigned by Keycloak when the user
     * is created. This ID is stable across sessions and should be used as the primary
     * user identifier for database foreign keys and audit trails.</p>
     *
     * @return an {@link Optional} containing the user ID, or empty if not authenticated
     */
    public static Optional<String> getCurrentUserId() {
        return getJwt().map(Jwt::getSubject);
    }

    /**
     * Returns the human-readable username of the currently authenticated user.
     *
     * <p>This is the {@code preferred_username} claim from the Keycloak JWT, or the
     * principal name from the authentication token. Suitable for display and logging
     * but NOT for database references (use {@link #getCurrentUserId()} instead).</p>
     *
     * @return an {@link Optional} containing the username, or empty if not authenticated
     */
    public static Optional<String> getCurrentUsername() {
        return getAuthentication().map(Authentication::getName);
    }

    /**
     * Returns the email address of the currently authenticated user.
     *
     * <p>Extracted from the {@code email} claim of the JWT. May be absent if the
     * {@code email} scope was not requested or the user has no email configured.</p>
     *
     * @return an {@link Optional} containing the email, or empty if not available
     */
    public static Optional<String> getCurrentEmail() {
        return getJwt().map(jwt -> jwt.getClaimAsString("email"));
    }

    /**
     * Returns all granted role names for the currently authenticated user.
     *
     * <p>The returned set contains the full role strings including the {@code ROLE_}
     * prefix (e.g., {@code ROLE_ADMIN}, {@code ROLE_CUSTOMER}). This matches the
     * format used in the {@link Roles} constants class.</p>
     *
     * @return an unmodifiable set of role names, or an empty set if not authenticated
     */
    public static Set<String> getCurrentRoles() {
        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toUnmodifiableSet()))
                .orElse(Collections.emptySet());
    }

    /**
     * Checks whether the current user has the {@code ROLE_ADMIN} role.
     *
     * @return {@code true} if the user is authenticated and has the admin role
     */
    public static boolean isAdmin() {
        return getCurrentRoles().contains(Roles.ADMIN);
    }

    /**
     * Checks whether the current user has the {@code ROLE_OPERATOR} role.
     *
     * @return {@code true} if the user is authenticated and has the operator role
     */
    public static boolean isOperator() {
        return getCurrentRoles().contains(Roles.OPERATOR);
    }

    /**
     * Checks whether the current user has the {@code ROLE_CUSTOMER} role.
     *
     * @return {@code true} if the user is authenticated and has the customer role
     */
    public static boolean isCustomer() {
        return getCurrentRoles().contains(Roles.CUSTOMER);
    }

    /**
     * Checks whether the current user has the {@code ROLE_FINANCE} role.
     *
     * @return {@code true} if the user is authenticated and has the finance role
     */
    public static boolean isFinance() {
        return getCurrentRoles().contains(Roles.FINANCE);
    }

    /**
     * Checks whether the current user has a specific role.
     *
     * @param role the role to check (should include the {@code ROLE_} prefix)
     * @return {@code true} if the user is authenticated and has the specified role
     */
    public static boolean hasRole(String role) {
        return getCurrentRoles().contains(role);
    }

    /**
     * Returns the raw JWT for the currently authenticated user, if available.
     *
     * <p>Useful when you need to access custom JWT claims that are not exposed
     * through the standard utility methods.</p>
     *
     * @return an {@link Optional} containing the JWT, or empty if not a JWT-based authentication
     */
    public static Optional<Jwt> getJwt() {
        return getAuthentication()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(JwtAuthenticationToken::getToken);
    }

    /**
     * Returns the current {@link Authentication} from the security context.
     *
     * @return an {@link Optional} containing the authentication, or empty if none exists
     */
    private static Optional<Authentication> getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authenticated user in SecurityContext");
            return Optional.empty();
        }
        return Optional.of(authentication);
    }
}
