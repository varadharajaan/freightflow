package com.freightflow.commons.security.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts Keycloak-issued JWTs into Spring Security {@link AbstractAuthenticationToken} instances.
 *
 * <p>Keycloak stores roles in a non-standard JWT claim structure that differs from the default
 * Spring Security expectations. This converter bridges the gap by extracting roles from both
 * realm-level and client-level claims and mapping them to {@link GrantedAuthority} objects.</p>
 *
 * <h3>Keycloak JWT Claim Structure</h3>
 * <pre>{@code
 * {
 *   "realm_access": {
 *     "roles": ["ROLE_ADMIN", "ROLE_OPERATOR"]
 *   },
 *   "resource_access": {
 *     "freightflow-api": {
 *       "roles": ["manage-bookings", "view-reports"]
 *     }
 *   },
 *   "preferred_username": "admin",
 *   "sub": "a1b2c3d4-e5f6-..."
 * }
 * }</pre>
 *
 * <h3>Role Mapping Rules</h3>
 * <ul>
 *   <li>Realm roles: extracted from {@code realm_access.roles}. If the role does not already
 *       start with {@code ROLE_}, the prefix is added (e.g., {@code ADMIN} → {@code ROLE_ADMIN}).</li>
 *   <li>Client roles: extracted from {@code resource_access.{clientId}.roles}. Prefixed with
 *       {@code ROLE_} the same way.</li>
 *   <li>Both are merged into a single authority collection for the authenticated principal.</li>
 * </ul>
 *
 * <h3>Principal Name</h3>
 * <p>The principal name is set to the JWT {@code preferred_username} claim if present,
 * falling back to the {@code sub} (subject) claim. This allows
 * {@code authentication.getName()} to return a human-readable username.</p>
 *
 * @see org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
 */
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationConverter.class);

    /** JWT claim key for Keycloak realm-level role access. */
    private static final String CLAIM_REALM_ACCESS = "realm_access";

    /** JWT claim key for Keycloak client/resource-level role access. */
    private static final String CLAIM_RESOURCE_ACCESS = "resource_access";

    /** Key within the access claim objects that holds the roles array. */
    private static final String CLAIM_ROLES = "roles";

    /** JWT claim key for the human-readable username. */
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    /** Spring Security role prefix. */
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Converts a Keycloak JWT into a Spring Security authentication token.
     *
     * <p>Extracts realm and client roles from the JWT claims and builds a
     * {@link JwtAuthenticationToken} with the derived authorities. The principal
     * name is resolved from {@code preferred_username} or {@code sub}.</p>
     *
     * @param jwt the decoded JWT from the OAuth2 resource server filter
     * @return a fully populated authentication token with granted authorities
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String principalName = resolvePrincipalName(jwt);

        log.debug("JWT converted for principal='{}' with {} authorities: {}",
                principalName, authorities.size(),
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(", ")));

        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }

    /**
     * Extracts all granted authorities from both realm-level and client-level JWT claims.
     *
     * @param jwt the decoded JWT
     * @return a combined collection of realm and client authorities
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        authorities.addAll(extractRealmRoles(jwt));
        authorities.addAll(extractClientRoles(jwt));

        if (authorities.isEmpty()) {
            log.warn("No roles found in JWT for subject='{}'. Check Keycloak client scope " +
                    "configuration — ensure 'roles' scope is assigned and realm/client role " +
                    "mappers are configured.", jwt.getSubject());
        }

        return Collections.unmodifiableList(authorities);
    }

    /**
     * Extracts realm-level roles from the {@code realm_access.roles} JWT claim.
     *
     * <p>Keycloak stores realm roles under:</p>
     * <pre>{@code
     * "realm_access": {
     *   "roles": ["ROLE_ADMIN", "ROLE_OPERATOR"]
     * }
     * }</pre>
     *
     * @param jwt the decoded JWT
     * @return list of realm-level granted authorities
     */
    @SuppressWarnings("unchecked")
    private List<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
        if (realmAccess == null) {
            log.debug("No '{}' claim found in JWT for subject='{}'", CLAIM_REALM_ACCESS, jwt.getSubject());
            return Collections.emptyList();
        }

        Object rolesObj = realmAccess.get(CLAIM_ROLES);
        if (!(rolesObj instanceof Collection<?>)) {
            log.warn("'{}' claim in JWT does not contain a valid '{}' array for subject='{}'",
                    CLAIM_REALM_ACCESS, CLAIM_ROLES, jwt.getSubject());
            return Collections.emptyList();
        }

        Collection<String> roles = (Collection<String>) rolesObj;
        List<GrantedAuthority> authorities = roles.stream()
                .map(this::toGrantedAuthority)
                .collect(Collectors.toList());

        log.debug("Extracted {} realm roles for subject='{}': {}",
                authorities.size(), jwt.getSubject(),
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(", ")));

        return authorities;
    }

    /**
     * Extracts client-level roles from the {@code resource_access.{clientId}.roles} JWT claims.
     *
     * <p>Keycloak stores per-client roles under:</p>
     * <pre>{@code
     * "resource_access": {
     *   "freightflow-api": {
     *     "roles": ["manage-bookings"]
     *   }
     * }
     * }</pre>
     *
     * @param jwt the decoded JWT
     * @return list of client-level granted authorities
     */
    @SuppressWarnings("unchecked")
    private List<GrantedAuthority> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(CLAIM_RESOURCE_ACCESS);
        if (resourceAccess == null) {
            log.debug("No '{}' claim found in JWT for subject='{}'", CLAIM_RESOURCE_ACCESS, jwt.getSubject());
            return Collections.emptyList();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();

        for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
            String clientId = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?>)) {
                continue;
            }

            Map<String, Object> clientAccess = (Map<String, Object>) entry.getValue();
            Object rolesObj = clientAccess.get(CLAIM_ROLES);
            if (!(rolesObj instanceof Collection<?>)) {
                continue;
            }

            Collection<String> roles = (Collection<String>) rolesObj;
            roles.stream()
                    .map(this::toGrantedAuthority)
                    .forEach(authorities::add);

            log.debug("Extracted {} client roles from client='{}' for subject='{}'",
                    roles.size(), clientId, jwt.getSubject());
        }

        return authorities;
    }

    /**
     * Converts a role string to a Spring Security {@link GrantedAuthority}.
     *
     * <p>If the role does not already start with {@code ROLE_}, the prefix is added.
     * This ensures compatibility with Spring Security's {@code hasRole()} expressions,
     * which automatically prepend {@code ROLE_} to the role name.</p>
     *
     * @param role the raw role name from the JWT
     * @return a {@link SimpleGrantedAuthority} with the appropriate prefix
     */
    private GrantedAuthority toGrantedAuthority(String role) {
        if (role.startsWith(ROLE_PREFIX)) {
            return new SimpleGrantedAuthority(role);
        }
        return new SimpleGrantedAuthority(ROLE_PREFIX + role);
    }

    /**
     * Resolves the principal name from the JWT, preferring {@code preferred_username}
     * over the default {@code sub} claim.
     *
     * @param jwt the decoded JWT
     * @return the resolved principal name
     */
    private String resolvePrincipalName(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        return jwt.getSubject();
    }
}
