# FreightFlow - Backlog & Future Enhancements

> This document tracks features that are planned but not yet implemented,
> hardcoded values that need to be replaced, and stubs that need full implementation.
> Each item has a corresponding GitHub issue for tracking.

---

## Not Yet Implemented (Planned Features)

| # | Feature | Current State | Target Topic | GitHub Issue |
|---|---|---|---|---|
| 1 | **Quote Generation Service** | Not started | New service needed | TBD |
| 2 | **JPA Persistence Adapter** | Port interface exists, no JPA entity/repository yet | T4 (JPA Advanced) | #3 |
| 3 | **Kafka Event Publisher** | Using Spring ApplicationEvent (local only) | T5 (Kafka) | #7 |
| 4 | **Booking History / Event Store** | Events collected but not persisted to event store | T3 (CQRS + ES) | #6 |
| 5 | **HATEOAS Links** | Not on responses yet | T8 (API Gateway) | #12 |
| 6 | **Pagination** | List endpoint returns all bookings (no cursor) | T25 (API Design) | #14 |
| 7 | **Authentication/Authorization** | No security on endpoints yet | T9 (Security) | #15 |
| 8 | **Rate Limiting** | Not implemented on API Gateway | T6 (Resilience) | #9 |
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
| 2 | `BookingServiceConfig.java` | Empty config class (placeholder) | Add ObjectMapper, Kafka, Resilience4j beans |
| 3 | `GlobalExceptionHandler` | `PROBLEM_BASE_URI` hardcoded | Move to config property |
| 4 | `Booking.create()` | No departure date min-days validation | Should be configurable (e.g., 7 days minimum) |
| 5 | `SpringEventBookingPublisher` | Uses Spring events (in-process only) | Replace with Kafka producer in T5 |

---

## Services Not Yet Created

| Service | Description | When |
|---|---|---|
| **quote-service** | Generate freight quotes based on route, container type, weight | Phase 2-3 |
| **tracking-service** | Real-time container position tracking | Phase 2 |
| **billing-service** | Invoice generation, payment processing | Phase 2-3 |
| **vessel-schedule-service** | Vessel routes and schedule management | Phase 3 |
| **customer-service** | Customer profiles, contracts, RBAC | Phase 3-4 |
| **notification-service** | Email, SMS, webhook notifications | Phase 3 |
| **api-gateway** | Spring Cloud Gateway with rate limiting | Phase 3 |
| **config-server** | Spring Cloud Config (Git-backed) | Phase 3 |
| **discovery-server** | Eureka Server | Phase 3 |
