# ADR-006: Contract-First API Design with OpenAPI 3.1

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow's microservices expose REST APIs consumed by other internal services, the web
frontend, mobile applications, and third-party logistics partners. API design consistency,
discoverability, and evolvability are critical for a platform with 8+ services and multiple
consumer teams.

### Requirements
- Consistent, well-documented APIs across all services
- API contracts available before implementation to unblock frontend/partner teams
- Machine-readable specifications for code generation (clients, SDKs, mocks)
- Support for non-breaking API evolution over time
- Discoverability — developers can find and understand APIs without reading source code
- Hypermedia-driven navigation for complex resource relationships (bookings → tracking → billing)

### Options Considered
1. **Code-First (annotations generate spec)** - Write Spring controllers with annotations
   (`@Operation`, `@Schema`), then generate the OpenAPI spec from code. Fast to start.
   However, the API design is tightly coupled to the implementation. Refactoring code
   changes the contract. The spec is a *byproduct*, not a *deliberate design artifact*.
   Consumer teams cannot start work until the service is coded. Encourages implementation-
   driven API shapes rather than consumer-driven design.
2. **Contract-First (spec drives code)** - Design the OpenAPI specification first as the
   source of truth. Generate server stubs and client code from the spec. Forces deliberate
   API design thinking. Decouples design from implementation timeline. Enables parallel
   workstreams. The spec is a versioned artifact reviewed like code.
3. **GraphQL** - Flexible query language allowing clients to request exactly the data they
   need. Excellent for frontend-heavy applications with diverse data needs. However, adds
   significant complexity (resolvers, DataLoader, N+1 query prevention), has caching
   challenges (no HTTP-level caching), and is overkill for service-to-service communication
   where data shapes are well-defined.
4. **gRPC** - High-performance binary protocol with Protobuf schemas. Excellent for
   service-to-service communication. However, not browser-friendly without a proxy (gRPC-Web),
   poor human readability, and less tooling for API exploration. May be adopted later for
   internal high-throughput paths.

## Decision

We will adopt **Contract-First API Design** with **OpenAPI 3.1** as the specification format
for all FreightFlow REST APIs.

### Contract-First Workflow
1. **Design** — API designer creates/modifies the OpenAPI 3.1 YAML spec in the service's
   `src/main/resources/openapi/` directory.
2. **Review** — Spec changes go through pull request review like any code change. API
   governance team reviews for consistency, naming conventions, and backward compatibility.
3. **Generate** — `openapi-generator-maven-plugin` generates:
   - **Server interfaces** (Spring Boot `@RestController` interfaces) that the service
     implements
   - **Client SDKs** (Java, TypeScript) published to an internal artifact repository
   - **Mock servers** (Prism or WireMock stubs) for consumer testing
4. **Implement** — Service developers implement the generated interfaces. The compiler
   enforces contract compliance.
5. **Validate** — CI pipeline validates that the running service's actual responses conform
   to the OpenAPI spec using Schemathesis or Dredd.

### OpenAPI 3.1 Specification Standards
```yaml
openapi: "3.1.0"
info:
  title: FreightFlow Booking API
  version: "1.0.0"
  description: Manages freight booking lifecycle
  contact:
    name: Booking Team
    email: booking-team@freightflow.com
servers:
  - url: /api/v1
    description: Versioned API base path
paths:
  /bookings:
    post:
      operationId: createBooking
      tags: [Bookings]
      summary: Create a new freight booking
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateBookingRequest'
      responses:
        '201':
          description: Booking created successfully
          content:
            application/hal+json:
              schema:
                $ref: '#/components/schemas/BookingResponse'
          links:
            GetBooking:
              operationId: getBooking
              parameters:
                bookingId: $response.body#/id
```

### Springdoc OpenAPI Integration
- **Springdoc** (`springdoc-openapi-starter-webmvc-ui`) serves the live OpenAPI spec and
  Swagger UI at `/swagger-ui.html` and `/v3/api-docs`.
- The rendered spec merges the contract-first YAML with runtime annotations for operational
  details (security schemes, server URLs).
- Configuration:
  ```yaml
  springdoc:
    api-docs:
      path: /v3/api-docs
    swagger-ui:
      path: /swagger-ui.html
      operations-sorter: method
      tags-sorter: alpha
    default-produces-media-type: application/hal+json
  ```

### HATEOAS (Hypermedia as the Engine of Application State)
- APIs return `application/hal+json` responses with `_links` sections guiding consumers
  to related resources.
- Spring HATEOAS (`spring-boot-starter-hateoas`) provides `EntityModel`, `CollectionModel`,
  and `WebMvcLinkBuilder` for assembling hypermedia responses.
- Example response:
  ```json
  {
    "id": "BK-2026-00142",
    "status": "CONFIRMED",
    "origin": "Shanghai",
    "destination": "Rotterdam",
    "_links": {
      "self": { "href": "/api/v1/bookings/BK-2026-00142" },
      "tracking": { "href": "/api/v1/tracking/shipments/SH-2026-00142" },
      "billing": { "href": "/api/v1/billing/invoices?bookingId=BK-2026-00142" },
      "cancel": { "href": "/api/v1/bookings/BK-2026-00142/cancel", "method": "POST" },
      "amend": { "href": "/api/v1/bookings/BK-2026-00142/amend", "method": "PUT" }
    }
  }
  ```
- HATEOAS enables client applications to discover available actions dynamically rather than
  hardcoding URLs, improving evolvability.

### API Versioning Strategy
- **URL-based versioning**: `/api/v1/bookings`, `/api/v2/bookings`.
- Major version increments only for breaking changes (field removal, type change, behavioral
  change).
- Non-breaking changes (adding optional fields, new endpoints) do not increment the version.
- Support at most **two concurrent major versions** in production (N and N-1). The older
  version is deprecated with a 6-month sunset period.
- Deprecation is communicated via:
  - `Deprecation` HTTP header on old version responses
  - `Sunset` HTTP header with the removal date
  - Developer portal announcements

### Naming Conventions
| Aspect          | Convention                          | Example                         |
|-----------------|-------------------------------------|---------------------------------|
| URLs            | Lowercase, kebab-case, plural nouns | `/api/v1/booking-requests`      |
| Query params    | camelCase                           | `?sortBy=createdAt&pageSize=20` |
| JSON fields     | camelCase                           | `bookingId`, `createdAt`        |
| Enums           | UPPER_SNAKE_CASE                    | `CONFIRMED`, `IN_TRANSIT`       |
| Date/time       | ISO 8601 with timezone              | `2026-03-27T14:30:00Z`         |
| Pagination      | Cursor-based for large datasets     | `?cursor=abc123&limit=20`       |

## Consequences

### Positive
- API contracts are deliberate, reviewed design artifacts — not accidental byproducts of code
- Frontend and partner teams can start development immediately using generated mocks/clients
- Generated server interfaces enforce compile-time contract compliance — implementation
  drift is impossible
- HATEOAS enables dynamic API discovery, reducing client-side hardcoded URL coupling
- OpenAPI 3.1 aligns with JSON Schema 2020-12, enabling schema reuse across API specs
  and event schemas
- Versioning strategy provides clear evolution path with predictable deprecation timelines
- Machine-readable specs enable automated API testing, documentation, and SDK generation

### Negative
- Upfront design effort is higher than code-first — teams must design the API before writing code
- OpenAPI generator produces verbose code that may not match team coding style preferences
- HATEOAS adds response payload size and complexity; not all consumers need hypermedia links
- Maintaining spec and implementation in sync requires CI validation (Schemathesis)
- URL-based versioning leads to code duplication when supporting multiple concurrent versions
- Generated client SDKs require a publishing pipeline (artifact repository, versioning)

### Mitigations
- Provide OpenAPI spec templates and a style guide to accelerate upfront design
- Customize `openapi-generator` templates to match FreightFlow code style and conventions
- Make HATEOAS links optional — clients can ignore `_links` if they don't need hypermedia
  navigation
- Add a CI step that validates live API responses against the OpenAPI spec (fail the build
  on contract violations)
- For multi-version support, use Spring's `@RequestMapping` with version-specific controller
  adapters sharing core business logic
- Publish generated SDKs to a private Maven/npm registry as part of the CI/CD pipeline with
  semantic versioning

## References
- [OpenAPI 3.1 Specification](https://spec.openapis.org/oas/v3.1.0)
- [Springdoc OpenAPI](https://springdoc.org/)
- [Spring HATEOAS Reference](https://docs.spring.io/spring-hateoas/docs/current/reference/html/)
- [OpenAPI Generator](https://openapi-generator.tech/)
- [API Design Patterns - JJ Geewax](https://www.manning.com/books/api-design-patterns)
- [Zalando RESTful API Guidelines](https://opensource.zalando.com/restful-api-guidelines/)
