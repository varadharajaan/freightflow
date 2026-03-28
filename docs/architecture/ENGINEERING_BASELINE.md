# FreightFlow — Principal Engineering Baseline Plan

## Status: IN PROGRESS

## Summary

Establish a monorepo-wide architecture contract first, then harden services against
concrete drift, while making build quality measurable and enforceable in CI.

**Primary goal:** strict SOLID/hexagonal boundaries, deterministic exception handling,
explicit extensibility seams, and reliable regression safety.

---

## Key Changes

### 1. Build/Runtime Baseline
- Standardize toolchain on Java 21 + Maven Wrapper at repo root
- Fail fast in CI if wrong Java/Maven version detected
- Single quality pipeline: compile → test → verify with profile rules
- Executable in dev (relaxed), strict in CI (fail on violations)

### 2. Shared Architecture Contract
- Convert exception handling into reusable auto-configuration starter
- Every service gets consistent RFC 7807 behavior without package-scan accidents
- ArchUnit rules in commons-testing enforce layer boundaries:
  - Domain independent of infrastructure
  - Application only via ports
  - Adapters isolated from each other

### 3. Service Architecture Hardening (vessel-first, then replicate)
- Explicit outbound event publisher port with transactional publishing (outbox-ready seam)
- Command-side cache eviction/invalidation contract aligned with query cache keys
- Resolve aggregate-persistence drift: persist route/port-calls correctly or remove until modeled
- Domain-specific exception factories for consistency and observability tags

### 4. Repository-Wide Maintainability
- Minimum testing baseline per service:
  - Domain unit tests (aggregate behavior, state machine)
  - Adapter integration slice tests (@DataJpaTest, @WebMvcTest)
  - API contract test (REST endpoint validation)
- Architecture/quality docs as enforceable standards (ArchUnit, not prose-only)
- SOLID/LSP expectations for ports/adapters coded as test rules

### 5. Observability Contract (Addition)
- Every service: health endpoint, Micrometer metrics, structured logging (MDC correlation ID)
- Virtual Thread configuration verified per service
- Distributed tracing headers propagated

### 6. API Contract (Addition)
- /api/v1/ URI versioning with Sunset header strategy
- Idempotency-Key header for POST/PATCH
- JWT validation at API Gateway; @PreAuthorize for RBAC
- p99 < 200ms reads, p99 < 500ms writes

---

## Test Plan

| Test | What It Validates |
|---|---|
| **Toolchain gate** | CI fails on non-Java-21 runtime and missing wrapper |
| **ArchUnit suite** | Dependency direction, adapter isolation, naming conventions |
| **Vessel regression** | Cache invalidation on commands, events published per transition, route data survives round-trip |
| **Exception compliance** | Each service returns ProblemDetail for 404/409/422/500 |
| **Smoke pipeline** | Monorepo compile + test + verify green on fresh checkout |

---

## Assumptions
- Java 21 remains target baseline
- Spring Boot 3.3.x line remains in use
- Architecture-first: enforce contracts early, even if velocity is temporarily slower
- Route modeling: persist and reconstitute correctly (no dropping state)
