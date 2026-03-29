# FreightFlow - Backlog & Future Enhancements

> This document tracks features that are planned but not yet implemented,
> hardcoded values that need to be replaced, and stubs that need full implementation.
> Each item has a corresponding GitHub issue for tracking.

---

## Not Yet Implemented (Planned Features)

| # | Feature | Current State | Target Topic | GitHub Issue |
|---|---|---|---|---|
| 1 | **Quote Generation Service** | Not started | New service needed | TBD |
| 2 | **JPA Persistence Adapter** | ✅ Completed (Issue #3 closed) — Fully implemented: BookingJpaEntity, JpaBookingPersistenceAdapter, BookingEntityMapper, JPA auditing, Flyway V1+V2 | T4 (JPA Advanced) | ✅ Closed |
| 3 | **Kafka Event Publisher** | ✅ Completed (Issue #7 closed) — KafkaBookingEventPublisher with exactly-once, DLQ, consumer with @KafkaListener, KRaft Docker Compose | T5 (Kafka) | ✅ Closed |
| 4 | **Booking History / Event Store** | ✅ Completed (Issue #6 closed) — Fully implemented: EventStore port, JpaEventStoreAdapter, BookingProjectionUpdater, BookingQueryHandler.getBookingHistory(), Flyway V3 | T3 (CQRS + ES) | ✅ Closed |
| 5 | **HATEOAS Links** | Not on responses yet | T8 (API Gateway) | #12 |
| 6 | **Pagination** | List endpoint returns all bookings (no cursor) | T25 (API Design) | #14 |
| 7 | **Authentication/Authorization** | ✅ Completed (Issue #15 closed) — OAuth2/OIDC with Keycloak, JWT validation, RBAC (4 roles), method-level @PreAuthorize, IDOR ownership enforcement, security headers (CSP/HSTS), CORS | T9 (Security) | ✅ Closed |
| 8 | **Rate Limiting** | ✅ Completed (Issue #9 closed) — Bucket4j Token Bucket (100 req/min), RFC 6585 headers, RateLimitingFilter | T6 (Resilience) | ✅ Closed |
| 9 | **Idempotency Key** | Header not checked on POST | T25 (API Design) | #14 |
| 10 | **ETag / Conditional Requests** | Not implemented | T25 (API Design) | #14 |
| 11 | **Bill of Lading Generation** | Not started | New feature | TBD |
| 12 | **Container Allocation** | Booking doesn't allocate specific containers | New feature | TBD |
| 13 | **Vessel Capacity Check** | No capacity validation on booking | New feature | TBD |

---

## Hardcoded Values / Stubs to Replace

| # | Location | What's Hardcoded | Replacement Needed |
|---|---|---|---|
| 1 | `application-local.yml` | DB credentials (freightflow/freightflow) | Vault/Secrets Manager in prod |
| 2 | `BookingServiceConfig.java` | Basic config with JPA auditing. Kafka, Resilience4j, ObjectMapper beans still needed. | Add ObjectMapper, Kafka, Resilience4j beans |
| 3 | `GlobalExceptionHandler` | `PROBLEM_BASE_URI` hardcoded | Move to config property |
| 4 | `Booking.create()` | No departure date min-days validation | Should be configurable (e.g., 7 days minimum) |
| 5 | `SpringEventBookingPublisher` | Uses Spring events (in-process only) | Replace with Kafka producer in T5 |

---

## Services Status

| Service | Description | Status |
|---|---|---|
| **booking-service** | Booking lifecycle with CQRS, Event Sourcing, Kafka | ✅ Implemented |
| **discovery-server** | Eureka Server (port 8761) with dashboard | ✅ Implemented (Issue #11) |
| **config-server** | Spring Cloud Config, Git-backed (port 8888) | ✅ Implemented (Issue #11) |
| **quote-service** | Generate freight quotes based on route, container type, weight | Planned (Phase 2-3) |
| **tracking-service** | Real-time container tracking, milestones, position updates | ✅ Implemented |
| **billing-service** | Invoicing, payments, double-entry ledger, Saga participant | ✅ Implemented |
| **vessel-schedule-service** | Fleet management, voyage scheduling, capacity management | ✅ Implemented |
| **customer-service** | Customer profiles, contracts, credit management, RBAC | ✅ Implemented |
| **notification-service** | Multi-channel alerts (email, SMS, webhook), pure Kafka consumer | ✅ Implemented |
| **api-gateway** | Spring Cloud Gateway with rate limiting | ✅ Implemented (Issue #12) |
