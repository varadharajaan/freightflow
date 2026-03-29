# FreightFlow Security Architecture

## Overview

FreightFlow uses **OAuth2/OpenID Connect (OIDC)** for authentication and **Role-Based Access Control (RBAC)** for authorization. Identity management is delegated to **Keycloak**, which serves as the centralized Identity Provider (IdP) for the entire platform.

Every microservice operates as an **OAuth2 Resource Server** — it validates incoming JWTs but never issues tokens. Token issuance is exclusively handled by Keycloak.

---

## Authentication Flow

```
┌──────────┐     ┌─────────────┐     ┌──────────────┐     ┌──────────────────┐
│  Client   │     │ API Gateway │     │   Keycloak   │     │  Microservice    │
│ (SPA/App) │     │  (port 8080)│     │ (port 8180)  │     │ (e.g. Booking)   │
└─────┬─────┘     └──────┬──────┘     └──────┬───────┘     └────────┬─────────┘
      │                   │                   │                      │
      │  1. Login (user/pass)                 │                      │
      │──────────────────────────────────────>│                      │
      │                   │                   │                      │
      │  2. JWT Access Token + Refresh Token  │                      │
      │<──────────────────────────────────────│                      │
      │                   │                   │                      │
      │  3. API Request + Authorization: Bearer <JWT>                │
      │──────────────────>│                   │                      │
      │                   │                   │                      │
      │                   │  4. Validate JWT (JWKS endpoint)         │
      │                   │──────────────────>│                      │
      │                   │  5. Public Key    │                      │
      │                   │<──────────────────│                      │
      │                   │                   │                      │
      │                   │  6. Forward request (JWT intact)         │
      │                   │─────────────────────────────────────────>│
      │                   │                   │                      │
      │                   │                   │  7. Validate JWT     │
      │                   │                   │     (cached JWKS)    │
      │                   │                   │  8. Extract roles    │
      │                   │                   │  9. @PreAuthorize    │
      │                   │                   │     check            │
      │                   │                   │                      │
      │  10. API Response │                   │                      │
      │<──────────────────│<─────────────────────────────────────────│
      │                   │                   │                      │
```

### Flow Steps

1. **Client authenticates** with Keycloak using username/password (direct grant) or authorization code flow (SPA with PKCE).
2. **Keycloak issues tokens** — a short-lived access token (5 min) and a longer-lived refresh token (30 min).
3. **Client sends API request** with the JWT in the `Authorization: Bearer <token>` header.
4. **API Gateway validates** the JWT signature using Keycloak's JWKS (JSON Web Key Set) endpoint.
5. **Keycloak returns public keys** — these are cached by the gateway and services.
6. **Gateway forwards** the validated request to the downstream microservice.
7. **Microservice validates** the JWT independently (defense in depth — does not trust the gateway alone).
8. **JwtAuthenticationConverter** extracts realm and client roles from Keycloak-specific JWT claims.
9. **@PreAuthorize** annotations enforce method-level RBAC.
10. **Response flows back** through the gateway to the client.

---

## JWT Claim Structure (Keycloak Format)

```json
{
  "exp": 1711612800,
  "iat": 1711612500,
  "iss": "http://localhost:8180/realms/freightflow",
  "sub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "preferred_username": "operator",
  "email": "operator@freightflow.com",
  "realm_access": {
    "roles": [
      "ROLE_OPERATOR"
    ]
  },
  "resource_access": {
    "freightflow-api": {
      "roles": [
        "manage-bookings"
      ]
    }
  },
  "scope": "openid profile email",
  "azp": "freightflow-api"
}
```

### Key Claims

| Claim | Description |
|-------|-------------|
| `sub` | Unique user ID (UUID) — use for database foreign keys |
| `preferred_username` | Human-readable username — use for display/logging |
| `realm_access.roles` | Realm-level roles (e.g., `ROLE_ADMIN`, `ROLE_OPERATOR`) |
| `resource_access.{client}.roles` | Client-specific roles (fine-grained per-service permissions) |
| `iss` | Token issuer — must match the configured `issuer-uri` |
| `exp` | Token expiration — Spring Security rejects expired tokens automatically |

---

## RBAC Matrix

### Booking Service

| Endpoint | Method | ADMIN | OPERATOR | CUSTOMER | FINANCE |
|----------|--------|:-----:|:--------:|:--------:|:-------:|
| `/api/v1/bookings` | POST (create) | Allowed | Allowed | Allowed | Denied |
| `/api/v1/bookings/{id}` | GET (read) | Allowed | Allowed | Allowed | Denied |
| `/api/v1/bookings/{id}/confirm` | POST (confirm) | Allowed | Allowed | **Denied** | Denied |
| `/api/v1/bookings/{id}` | DELETE (cancel) | Allowed | Allowed | Allowed | Denied |
| `/api/v1/bookings?customerId=X` | GET (list) | Allowed | Allowed | **Own only** | Denied |

### Row-Level Security

- **Customer list bookings**: The SpEL expression `#customerId == authentication.name` ensures customers can only query their own bookings. Admins and operators can query any customer's bookings.

### Planned Service RBAC (Future)

| Service | ADMIN | OPERATOR | CUSTOMER | FINANCE |
|---------|:-----:|:--------:|:--------:|:-------:|
| Billing | Full | Read | Own invoices | Full |
| Tracking | Full | Full | Own shipments | Read |
| Vessels | Full | Full | Read schedules | Denied |
| Customers | Full | Read | Own profile | Read |

---

## Roles

| Role | Description | Typical User |
|------|-------------|-------------|
| `ROLE_ADMIN` | Full system access — all CRUD operations, user management, system config | Platform administrators |
| `ROLE_OPERATOR` | Operational access — manage bookings, vessels, tracking | Logistics managers, dispatchers |
| `ROLE_CUSTOMER` | Customer access — manage own bookings, view schedules | Shippers, freight forwarders |
| `ROLE_FINANCE` | Finance access — billing, invoices, payments | Accounts team, CFO |

---

## Security Headers

All responses include the following security headers:

| Header | Value | Purpose |
|--------|-------|---------|
| `Content-Security-Policy` | `default-src 'self'` | Restricts resource loading to same-origin, preventing XSS via injected external scripts |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Forces HTTPS for 1 year, including subdomains |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME-type sniffing, reducing drive-by download risk |
| `X-Frame-Options` | `DENY` | Prevents iframe embedding, mitigating clickjacking |
| `X-XSS-Protection` | `0` | Disables legacy XSS auditor (deprecated, CSP replaces it) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Limits referrer leakage to origin-only for cross-origin requests |

---

## CORS Configuration

### Properties

```yaml
freightflow:
  security:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:8080}
```

### Details

| Setting | Value | Notes |
|---------|-------|-------|
| Allowed Origins | Configurable via property | Default: localhost:3000 (SPA), localhost:8080 (gateway) |
| Allowed Methods | GET, POST, PUT, DELETE, PATCH, OPTIONS | Full REST method support |
| Allowed Headers | Authorization, Content-Type, X-Correlation-ID, Idempotency-Key, X-Tenant-ID | Standard + FreightFlow custom headers |
| Exposed Headers | X-Correlation-ID, X-RateLimit-Remaining, X-RateLimit-Limit, X-RateLimit-Reset | Rate-limit + tracing headers visible to JS clients |
| Max Age | 3600s (1 hour) | Preflight cache duration |
| Credentials | true (when origins are not wildcard) | Required for Authorization header |

---

## Keycloak Clients

| Client ID | Type | Purpose |
|-----------|------|---------|
| `freightflow-api` | Confidential | Backend service-to-service communication. Has client secret, service account enabled. |
| `freightflow-gateway` | Public | SPA and mobile apps authenticating via the API gateway. Uses PKCE for security. |

### Token Lifetimes

| Token | Lifetime | Notes |
|-------|----------|-------|
| Access Token | 5 minutes | Short-lived for security. Refresh before expiry. |
| Refresh Token | 30 minutes (idle) | Extends session without re-authentication |
| SSO Session | 10 hours (max) | Maximum session duration |

---

## How to Add New Roles

1. **Add to Keycloak realm** — Edit `infrastructure/keycloak/freightflow-realm.json`:
   ```json
   {
     "name": "ROLE_AUDITOR",
     "description": "Audit access — read-only access to all resources for compliance.",
     "composite": false,
     "clientRole": false
   }
   ```

2. **Add to Roles constants** — Update `commons-security/.../rbac/Roles.java`:
   ```java
   public static final String AUDITOR = "ROLE_AUDITOR";
   public static final String HAS_AUDITOR = "hasRole('AUDITOR')";
   ```

3. **Add @PreAuthorize annotations** — Update controller methods:
   ```java
   @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
   public ResponseEntity<AuditLog> getAuditLog(...) { ... }
   ```

4. **Add test users** — Edit `freightflow-realm.json` users section.

5. **Write security tests** — Add `@WithMockUser(roles = "AUDITOR")` tests.

6. **Restart Keycloak** with `--import-realm` to load the updated configuration.

---

## How to Test Locally

### Option 1: Disable Security (Fastest)

Set the local profile to disable JWT validation:

```yaml
# application-local.yml
freightflow:
  security:
    enabled: false
```

Run with: `--spring.profiles.active=local`

### Option 2: Run Keycloak via Docker Compose

```bash
cd infrastructure/docker
docker compose up -d keycloak
```

Wait for Keycloak to start (health check passes), then obtain a token:

```bash
# Get access token for 'operator' user
curl -s -X POST http://localhost:8180/realms/freightflow/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=freightflow-api" \
  -d "client_secret=freightflow-api-secret" \
  -d "username=operator" \
  -d "password=operator123" \
  | jq -r '.access_token'
```

Use the token in API requests:

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/freightflow/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=freightflow-api" \
  -d "client_secret=freightflow-api-secret" \
  -d "username=operator" \
  -d "password=operator123" \
  | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/bookings
```

### Option 3: Unit Tests with @WithMockUser

Tests use `@WithMockUser` to simulate authenticated users without Keycloak:

```java
@Test
@WithMockUser(roles = "OPERATOR")
void should_Return200_When_OperatorConfirms() throws Exception {
    // No Keycloak needed — Spring Security injects a mock authentication
    mockMvc.perform(post("/api/v1/bookings/{id}/confirm", bookingId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
}
```

---

## Test Users (Development Only)

| Username | Password | Role | Email |
|----------|----------|------|-------|
| admin | admin123 | ROLE_ADMIN | admin@freightflow.com |
| operator | operator123 | ROLE_OPERATOR | operator@freightflow.com |
| customer1 | customer123 | ROLE_CUSTOMER | customer1@freightflow.com |
| finance | finance123 | ROLE_FINANCE | finance@freightflow.com |

> **Warning**: These credentials are for development and testing only. Production environments must use strong passwords and disable direct-grant authentication.

---

## Module Structure

```
commons-security/
├── src/main/java/com/freightflow/commons/security/
│   ├── config/
│   │   ├── FreightFlowSecurityAutoConfiguration.java  # Auto-config entry point
│   │   ├── JwtAuthenticationConfig.java               # SecurityFilterChain + JWT
│   │   ├── CorsConfig.java                            # CORS configuration
│   │   └── SecurityHeadersConfig.java                 # HTTP security headers
│   ├── jwt/
│   │   └── JwtAuthenticationConverter.java            # Keycloak JWT → Spring authorities
│   └── rbac/
│       ├── Roles.java                                 # Role constants + SpEL expressions
│       └── CurrentUser.java                           # SecurityContext utility
└── src/main/resources/
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Services consume this via a single dependency:

```xml
<dependency>
    <groupId>com.freightflow</groupId>
    <artifactId>commons-security</artifactId>
</dependency>
```

No additional configuration is needed — the auto-configuration registers all security beans automatically.
