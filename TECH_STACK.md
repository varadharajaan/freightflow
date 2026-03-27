# FreightFlow - Complete Production-Grade Technology Stack

> This document lists EVERY technology, tool, and framework used in the FreightFlow platform.
> Everything here is what top-tier enterprise shipping platforms
> run in production.

---

## CORE APPLICATION STACK

### Language & Runtime
| Technology | Version | Purpose |
|---|---|---|
| **Java** | 21 (LTS) | Primary language - Virtual Threads, Records, Sealed Classes, Pattern Matching |
| **GraalVM** | 21 | Native image compilation for selected services (faster startup) |
| **Maven** | 3.9+ | Build tool with Maven Wrapper (`mvnw`) |
| **Maven BOM** | Custom | Centralized dependency version management |

### Spring Ecosystem
| Technology | Version | Purpose |
|---|---|---|
| **Spring Boot** | 3.3+ | Application framework |
| **Spring Framework** | 6.1+ | Core DI, AOP, transaction management |
| **Spring Web MVC** | 6.1+ | REST API controllers |
| **Spring WebFlux** | 6.1+ | Reactive endpoints (tracking service, WebSocket) |
| **Spring Data JPA** | 3.3+ | Repository abstraction over Hibernate |
| **Spring Data Redis** | 3.3+ | Redis cache/session integration |
| **Spring Security** | 6.3+ | Authentication, authorization, OAuth2 resource server |
| **Spring Kafka** | 3.2+ | Kafka producer/consumer abstraction |
| **Spring Cloud Gateway** | 4.1+ | API Gateway (replaces Zuul) |
| **Spring Cloud Config Server** | 4.1+ | Centralized externalized configuration (Git-backed) |
| **Spring Cloud Config Client** | 4.1+ | `@RefreshScope`, dynamic config reload |
| **Spring Cloud Circuit Breaker** | 3.1+ | Circuit breaker abstraction (Resilience4j backend) |
| **Spring HTTP Interface Clients** | 6.1+ | Declarative REST clients (`@HttpExchange`) - replaces OpenFeign |
| **Micrometer Tracing** | 1.3+ | Distributed tracing propagation (replaces Sleuth) |
| **Spring Cloud Stream** | 4.1+ | Message-driven microservice abstraction |
| **Spring Cloud Contract** | 4.1+ | Consumer-driven contract testing |
| **Spring Actuator** | 3.3+ | Health checks, metrics, info endpoints |
| **Spring Retry** | 2.0+ | Retry logic for transient failures |
| **Spring Validation** | 3.3+ | Bean Validation (Jakarta Validation 3.0) |
| **Spring AOP** | 6.1+ | Cross-cutting concerns (logging, auditing, metrics) |
| **Spring Cache** | 6.1+ | Cache abstraction with Redis/Caffeine backends |
| **Spring WebSocket** | 6.1+ | Real-time container tracking updates |

---

## NETFLIX OSS & SERVICE DISCOVERY

> **Important**: As of 2026, only **Eureka** survives from Netflix OSS in Spring Cloud.
> Ribbon, Hystrix, and Zuul are **deprecated/removed**. We use their modern replacements.

| Netflix Original | Status | Modern Replacement Used | Why |
|---|---|---|---|
| **Eureka** | ACTIVE (v2.0.6) | **Eureka Server + Client** | Still best-in-class for Spring Cloud service discovery |
| **Ribbon** | DEPRECATED | **Spring Cloud LoadBalancer** | Netflix in maintenance mode since 2018 |
| **Hystrix** | DEPRECATED | **Resilience4j** (via Spring Cloud Circuit Breaker) | No longer actively developed |
| **Zuul** | REMOVED | **Spring Cloud Gateway** | Zuul 1 was blocking; Gateway is reactive |
| **OpenFeign** | FEATURE-COMPLETE | **Spring HTTP Interface Clients** (`@HttpExchange`) | Spring recommends migration to native `@HttpExchange` |

### What We Actually Use
| Technology | Purpose | Notes |
|---|---|---|
| **Eureka Server** | Service registry & discovery | Netflix OSS - services register themselves |
| **Eureka Client** | Service registration | Embedded in each microservice |
| **Spring Cloud LoadBalancer** | Client-side load balancing | Round-robin, weighted, zone-based, sticky sessions |
| **Spring HTTP Interface Clients** | Declarative HTTP clients (`@HttpExchange`) | Built into Spring Framework 6+, no extra dependency |
| **Spring Cloud Gateway** | API Gateway (reactive + MVC modes) | Route predicates, filters, rate limiting |
| **Spring Cloud Config Server** | Centralized externalized configuration | Git-backed, encryption support, refresh scope |
| **Spring Cloud Config Client** | Config consumption | `@RefreshScope`, dynamic property reload |

---

## RESILIENCE & FAULT TOLERANCE

| Technology | Purpose | Pattern |
|---|---|---|
| **Resilience4j CircuitBreaker** | Circuit breaking for external calls | Circuit Breaker Pattern |
| **Resilience4j RateLimiter** | Rate limiting at service level | Rate Limiter Pattern |
| **Resilience4j Retry** | Automatic retry with backoff | Retry Pattern |
| **Resilience4j Bulkhead** | Thread/semaphore isolation | Bulkhead Pattern |
| **Resilience4j TimeLimiter** | Timeout management | Timeout Pattern |

---

## DATA LAYER

### Databases
| Technology | Version | Purpose |
|---|---|---|
| **PostgreSQL** | 16+ | Primary relational database (database-per-service) |
| **PostgreSQL JSONB** | - | Semi-structured event storage, flexible schemas |
| **PostgreSQL Partitioning** | - | Table partitioning for large event/tracking tables |
| **PostgreSQL Full-Text Search** | - | Booking/customer search without Elasticsearch |
| **Redis** | 7+ | Distributed caching, session store, rate limiting |
| **Redis Sentinel** | 7+ | Redis high availability |

### ORM & Data Access
| Technology | Version | Purpose |
|---|---|---|
| **Hibernate ORM** | 6.4+ | JPA implementation |
| **Hibernate Envers** | 6.4+ | Entity auditing & versioning |
| **Hibernate Second-Level Cache** | - | Entity caching with Redis/Caffeine |
| **Spring Data JPA** | 3.3+ | Repository pattern, Specifications, Projections |
| **Spring Data JPA Auditing** | - | `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy` |
| **QueryDSL** | 5.1+ | Type-safe dynamic queries |
| **HikariCP** | 5.1+ | High-performance JDBC connection pooling |
| **Flyway** | 10+ | Database schema migration (versioned + repeatable) |
| **p6spy** | 3.9+ | SQL query logging & performance monitoring (dev) |

---

## MESSAGING & EVENT-DRIVEN ARCHITECTURE

| Technology | Version | Purpose |
|---|---|---|
| **Apache Kafka** | 3.7+ | Event backbone, inter-service messaging |
| **Kafka Streams** | 3.7+ | Stream processing (tracking service) |
| **Confluent Schema Registry** | 7.6+ | Avro/Protobuf schema management & evolution |
| **Apache Avro** | 1.11+ | Event serialization (schema evolution support) |
| **Spring Kafka** | 3.2+ | Producer/consumer abstraction |
| **Kafka Connect** | 3.7+ | CDC (Change Data Capture) from PostgreSQL |
| **Debezium** | 2.5+ | CDC connector for Outbox pattern |
| **Dead Letter Topics** | - | Failed message handling |
| **Exactly-Once Semantics** | - | Kafka transactional producers |

---

## API & INTEGRATION

| Technology | Purpose |
|---|---|
| **OpenAPI 3.1** (Springdoc) | API specification (contract-first) |
| **Swagger UI** | Interactive API documentation |
| **HATEOAS** (Spring HATEOAS) | Hypermedia-driven REST APIs |
| **gRPC** | High-performance inter-service communication (internal) |
| **Protobuf** | gRPC serialization format |
| **GraphQL** (Spring for GraphQL) | Flexible querying for customer portal |
| **WebSocket** (STOMP) | Real-time tracking updates |
| **Server-Sent Events (SSE)** | One-way real-time notifications |
| **WireMock** | API mocking for integration tests |

---

## SECURITY

| Technology | Purpose |
|---|---|
| **Keycloak** | Identity & Access Management (OAuth 2.0 / OIDC provider) |
| **Spring Security OAuth2 Resource Server** | JWT token validation |
| **Spring Security Method Security** | `@PreAuthorize`, `@Secured` annotations |
| **RBAC** (Role-Based Access Control) | Fine-grained authorization |
| **BCrypt** | Password hashing |
| **JWT (JSON Web Tokens)** | Stateless authentication tokens |
| **CORS Configuration** | Cross-origin request handling |
| **Rate Limiting** (Resilience4j + Redis) | API abuse prevention |
| **AWS Secrets Manager / HashiCorp Vault** | Secrets management |
| **OWASP Dependency Check** | Vulnerability scanning |
| **Spring Security Headers** | CSP, HSTS, X-Frame-Options, X-Content-Type-Options |

---

## CONTAINERIZATION & ORCHESTRATION

### Docker
| Technology | Purpose |
|---|---|
| **Docker** | Container runtime |
| **Multi-stage Dockerfile** | Optimized builds (build -> runtime separation) |
| **Distroless Base Image** (`gcr.io/distroless/java21`) | Minimal attack surface, no shell |
| **Docker Compose** | Local development environment (all services + infra) |
| **Jib** (Google) | OCI image building without Dockerfile (Maven plugin) |

### Kubernetes (K8s)
| Technology | Purpose |
|---|---|
| **Kubernetes** | Container orchestration platform |
| **Helm Charts** (v3) | K8s package management & templating |
| **Kustomize** | Environment-specific K8s overlays |
| **Deployments** | Rolling update strategy for services |
| **StatefulSets** | Kafka, PostgreSQL (if self-managed) |
| **ConfigMaps** | Non-sensitive configuration |
| **Secrets** | Sensitive configuration |
| **Ingress** (NGINX Ingress Controller) | External traffic routing |
| **HPA** (Horizontal Pod Autoscaler) | Auto-scaling based on CPU/memory/custom metrics |
| **PDB** (Pod Disruption Budgets) | Availability during cluster maintenance |
| **Network Policies** | Pod-to-pod network segmentation |
| **Resource Quotas & Limits** | CPU/memory resource management |
| **Liveness Probes** | Restart unhealthy containers |
| **Readiness Probes** | Route traffic only to ready pods |
| **Startup Probes** | Slow-starting service support |
| **Init Containers** | Pre-startup tasks (DB migration, config loading) |

### Service Mesh
| Technology | Purpose |
|---|---|
| **Istio** | Service mesh for traffic management, security, observability |
| **Envoy Proxy** | Sidecar proxy (deployed automatically by Istio) |
| **mTLS** (Mutual TLS) | Automatic encryption between all services |
| **Traffic Splitting** | Canary deployments, A/B testing |
| **Fault Injection** | Chaos testing (delays, aborts) |
| **Circuit Breaking** (Istio-level) | Mesh-level circuit breaking |
| **Rate Limiting** (Istio-level) | Mesh-level rate limiting |
| **Istio VirtualService** | Advanced routing rules |
| **Istio DestinationRule** | Load balancing, connection pool settings |
| **Istio Gateway** | Edge traffic management |
| **Kiali** | Service mesh observability dashboard |

---

## CI/CD & GITOPS

| Technology | Purpose |
|---|---|
| **GitHub Actions** | CI pipeline (build, test, quality, security scan) |
| **ArgoCD** | GitOps continuous delivery to Kubernetes |
| **ArgoCD ApplicationSet** | Multi-environment deployments |
| **ArgoCD Image Updater** | Automatic image tag updates |
| **Argo Rollouts** | Progressive delivery (canary, blue/green) |
| **SonarQube** | Code quality & security analysis |
| **Trivy** | Container image vulnerability scanning |
| **OWASP Dependency Check** | Library vulnerability scanning |
| **JaCoCo** | Code coverage reporting |
| **Checkstyle** | Code style enforcement |
| **SpotBugs** | Static bug detection |
| **PITest (PIT)** | Mutation testing |
| **GitHub Container Registry (GHCR)** | Docker image registry |
| **Semantic Versioning** | Automated versioning |
| **Conventional Commits** | Standardized commit messages |

### GitOps Workflow
```
Developer -> PR -> GitHub Actions (CI) -> Build & Test -> Push Image to GHCR
                                                              |
ArgoCD watches Git repo -> Detects manifest change -> Syncs to K8s cluster
                                                              |
Argo Rollouts -> Canary deployment (10% -> 50% -> 100%) -> Production
```

---

## CLOUD INFRASTRUCTURE (AWS)

| AWS Service | Purpose |
|---|---|
| **EKS** (Elastic Kubernetes Service) | Managed Kubernetes cluster |
| **RDS for PostgreSQL** | Managed PostgreSQL with Multi-AZ |
| **MSK** (Managed Streaming for Kafka) | Managed Apache Kafka |
| **ElastiCache for Redis** | Managed Redis cluster |
| **S3** | Object storage (documents, reports, backups) |
| **CloudFront** | CDN for static assets |
| **Route 53** | DNS management |
| **ACM** (Certificate Manager) | TLS certificate management |
| **IAM** | Identity & access management |
| **VPC** | Network isolation |
| **ALB** (Application Load Balancer) | Layer 7 load balancing |
| **CloudWatch** | AWS-native logging & alarms |
| **SNS / SQS** | Notification & queuing (supplementary) |
| **ECR** | Container image registry (alternative to GHCR) |
| **Secrets Manager** | Secrets storage & rotation |
| **KMS** | Encryption key management |
| **WAF** | Web Application Firewall |

### Infrastructure as Code
| Technology | Purpose |
|---|---|
| **Terraform** | Infrastructure provisioning (AWS resources) |
| **Terraform Modules** | Reusable infrastructure components |
| **Terraform Remote State** (S3 + DynamoDB) | State management |
| **Terragrunt** | Terraform wrapper for DRY configurations |
| **tfsec** | Terraform security scanning |
| **Checkov** | IaC policy enforcement |

---

## OBSERVABILITY (Three Pillars)

### Metrics
| Technology | Purpose |
|---|---|
| **Micrometer** | Application metrics instrumentation |
| **Prometheus** | Metrics collection & storage |
| **Grafana** | Metrics visualization & dashboards |
| **Custom Metrics** | Business metrics (bookings/min, revenue, SLA) |
| **JVM Metrics** | Heap, GC, threads, Virtual Thread pool |
| **Kafka Metrics** | Consumer lag, throughput, partition health |
| **PostgreSQL Metrics** | Connection pool, query performance |
| **HPA Custom Metrics** | Prometheus Adapter for K8s autoscaling |

### Logging
| Technology | Purpose |
|---|---|
| **SLF4J** | Logging facade |
| **Logback** | Logging implementation |
| **Logstash Logback Encoder** | JSON structured logging |
| **ELK Stack** (Elasticsearch + Logstash + Kibana) | Log aggregation & search |
| **Fluent Bit** | Log collection from K8s pods |
| **MDC** (Mapped Diagnostic Context) | Correlation ID, tenant ID, user ID |

### Distributed Tracing
| Technology | Purpose |
|---|---|
| **OpenTelemetry** | Vendor-neutral tracing instrumentation |
| **Jaeger** | Distributed trace collection & visualization |
| **Micrometer Tracing** (replaces Sleuth) | Spring Boot tracing integration |
| **Trace Context Propagation** | W3C Trace Context across HTTP, Kafka, gRPC |

### Alerting
| Technology | Purpose |
|---|---|
| **Prometheus Alertmanager** | Alert routing & notification |
| **PagerDuty** integration | On-call incident management |
| **Grafana Alerts** | Dashboard-based alerting |

---

## TESTING STACK

| Technology | Purpose | Test Level |
|---|---|---|
| **JUnit 5** | Test framework | All levels |
| **Mockito** | Mocking framework | Unit tests |
| **AssertJ** | Fluent assertions | All levels |
| **Testcontainers** | Docker-based integration tests | Integration |
| **Testcontainers PostgreSQL** | Real PostgreSQL in tests | Integration |
| **Testcontainers Kafka** | Real Kafka in tests | Integration |
| **Testcontainers Redis** | Real Redis in tests | Integration |
| **Testcontainers Keycloak** | Real Keycloak in tests | Integration |
| **WireMock** | HTTP API mocking | Integration |
| **Spring Cloud Contract** | Consumer-driven contract tests | Contract |
| **ArchUnit** | Architecture rule enforcement | Architecture |
| **PITest (PIT)** | Mutation testing (test quality) | Mutation |
| **Gatling** | Performance / load testing | Performance |
| **Awaitility** | Async testing utilities | Integration |
| **REST Assured** | REST API testing DSL | Integration |
| **JsonPath / JSONAssert** | JSON assertion | All levels |
| **Cucumber** (optional) | BDD acceptance tests | Acceptance |

---

## DESIGN PATTERNS USED

### Creational
| Pattern | Where Used |
|---|---|
| **Factory Method** | `BookingFactory`, `EventFactory` |
| **Abstract Factory** | `NotificationChannelFactory` (Email, SMS, Webhook) |
| **Builder** | Complex DTOs, query builders, event builders |
| **Singleton** | Spring beans (default scope), configuration holders |
| **Prototype** | Booking templates, schedule templates |

### Structural
| Pattern | Where Used |
|---|---|
| **Adapter** | External API integration (port terminal systems) |
| **Decorator** | Logging decorator, caching decorator, retry decorator |
| **Facade** | `BookingFacade` simplifying complex booking workflow |
| **Proxy** | JPA lazy loading, Spring AOP proxies |
| **Composite** | Pricing rules composition |

### Behavioral
| Pattern | Where Used |
|---|---|
| **Strategy** | `PricingStrategy`, `RoutingStrategy`, `NotificationStrategy` |
| **Observer** | Domain event listeners, Kafka consumers |
| **Command** | CQRS commands (`CreateBookingCommand`, `CancelBookingCommand`) |
| **Chain of Responsibility** | Validation chain, security filter chain |
| **Template Method** | Base service classes, report generators |
| **State** | Booking state machine (DRAFT -> CONFIRMED -> SHIPPED -> DELIVERED) |
| **Mediator** | Spring's `ApplicationEventPublisher` |

### Enterprise / Distributed
| Pattern | Where Used |
|---|---|
| **CQRS** | Booking service (separate command/query models) |
| **Event Sourcing** | Booking aggregate event store |
| **Saga** (Orchestration) | Cross-service booking+billing transaction |
| **Outbox** | Reliable event publishing (Debezium CDC) |
| **Circuit Breaker** | External service calls (Resilience4j) |
| **Bulkhead** | Thread isolation for critical paths |
| **Retry with Backoff** | Transient failure recovery |
| **Repository** | Data access abstraction (Spring Data JPA) |
| **Unit of Work** | JPA/Hibernate transaction management |
| **Specification** | Dynamic query building (JPA Specifications) |
| **Domain Event** | Aggregate state change notifications |
| **Anti-Corruption Layer** | External system integration boundaries |

---

## ARCHITECTURE STYLES

| Style | Where Applied |
|---|---|
| **Microservices Architecture** | Overall system decomposition |
| **Domain-Driven Design (DDD)** | Bounded contexts, aggregates, value objects |
| **Event-Driven Architecture** | Kafka-based async communication |
| **CQRS** | Booking service read/write separation |
| **Hexagonal Architecture** (Ports & Adapters) | Each service's internal structure |
| **API-First Design** | OpenAPI contracts before implementation |
| **Database-per-Service** | Data isolation and autonomy |
| **Strangler Fig** (migration pattern) | Documented for monolith decomposition |
| **Sidecar** | Istio Envoy proxy |
| **Ambassador** | API Gateway pattern |
| **Backend for Frontend (BFF)** | Customer portal API aggregation |

---

## COOL UTILITIES & LIBRARIES

> Beyond the standard stack - these set a Principal Engineer apart from Senior Engineers.

### Developer Productivity
| Technology | Purpose | Why It's Cool |
|---|---|---|
| **MapStruct** | Compile-time bean mapping (DTO <-> Entity) | Zero reflection, type-safe, 10x faster than ModelMapper |
| **Lombok** (selective) | Boilerplate reduction (`@Slf4j`, `@Builder`) | Used sparingly alongside Java 21 Records |
| **jOOQ** | Type-safe SQL builder | Complex queries that JPA/JPQL can't express cleanly |
| **Caffeine** | In-process caching (L1 before Redis L2) | Highest performance JVM cache, near-optimal hit rates |
| **Guava** | Google core utilities | `RateLimiter`, `Cache`, `Preconditions`, immutable collections |
| **Apache Commons Lang3** | String/Object/Reflection utilities | `StringUtils`, `ObjectUtils`, `ToStringBuilder` |
| **Jackson Databind** | JSON serialization/deserialization | Custom serializers, mixins, polymorphic handling |
| **Vavr** | Functional programming for Java | `Either<L,R>`, `Try<T>`, pattern matching, persistent collections |

### Code Generation & Validation
| Technology | Purpose | Why It's Cool |
|---|---|---|
| **Immutables** | Compile-time immutable value objects | `@Value.Immutable` generates builders, equals, hashCode |
| **Jakarta Bean Validation 3.0** | Declarative input validation | Custom validators, cross-field validation, groups |
| **Hibernate Validator** | Bean Validation reference implementation | Programmatic + annotation-based validation |
| **Springdoc OpenAPI** | Auto-generate OpenAPI from code | Swagger UI + contract-first with annotations |

### Performance & Monitoring
| Technology | Purpose | Why It's Cool |
|---|---|---|
| **JMH** (Java Microbenchmark Harness) | Micro-benchmarking framework | Prove performance claims with data, not guesses |
| **Async Profiler** | Low-overhead JVM profiler | CPU, heap, lock profiling in production |
| **Spring Boot DevTools** | Hot reload in development | LiveReload, auto-restart, H2 console |
| **Actuator Custom Endpoints** | Custom health/info/metrics endpoints | Business health indicators |

### Distributed Systems Utilities
| Technology | Purpose | Why It's Cool |
|---|---|---|
| **Spring Modulith** | Modular monolith support | Logical modules with enforced boundaries, easy to split later |
| **Spring Cloud Function** | Write once, deploy anywhere (Lambda, Azure Functions, K8s) | Serverless portability |
| **Spring Cloud Vault** | HashiCorp Vault integration | Dynamic secrets, secret rotation |
| **Spring Cloud Kubernetes** | K8s-native config & discovery | ConfigMaps/Secrets as Spring properties |
| **Spring Statemachine** | State machine framework | Booking lifecycle, order workflows |
| **Bucket4j** | Rate limiting with distributed support | Token bucket algorithm, Redis/Hazelcast backends |
| **Shedlock** | Distributed scheduled task locking | Prevents duplicate cron execution across instances |
| **Redisson** | Advanced Redis client | Distributed locks, semaphores, queues, bloom filters |

### Documentation & Diagrams
| Technology | Purpose | Why It's Cool |
|---|---|---|
| **Asciidoctor** | Technical documentation | ADRs, runbooks with includes, diagrams |
| **PlantUML** | Diagrams as code | Sequence, class, component diagrams in version control |
| **Spring REST Docs** | Test-driven API documentation | API docs generated from passing tests, always accurate |

---

## TOTAL COUNT

| Category | Count |
|---|---|
| **Spring Technologies** | 22+ |
| **Netflix OSS (Active + Replacements)** | 7 |
| **Data Technologies** | 12+ |
| **Messaging Technologies** | 9+ |
| **Security Technologies** | 11+ |
| **Container & Orchestration** | 18+ |
| **Service Mesh** | 10+ |
| **CI/CD & GitOps** | 15+ |
| **AWS Services** | 16+ |
| **Observability** | 14+ |
| **Testing** | 16+ |
| **Cool Utilities & Libraries** | 25+ |
| **Design Patterns** | 25+ |
| **Architecture Styles** | 11+ |
| **TOTAL UNIQUE TECHNOLOGIES** | **150+** |

---

## WHAT MAKES THIS PRINCIPAL ENGINEER LEVEL?

1. **Not just using tools - making architectural decisions about WHY** each tool was chosen (documented in ADRs)
2. **Production-grade concerns**: Security, observability, resilience, scalability - not just "it works"
3. **GitOps with ArgoCD**: Not just CI - full continuous delivery with progressive rollouts
4. **Service Mesh (Istio)**: mTLS, traffic management, chaos testing - enterprise networking
5. **Event-Driven with guarantees**: Exactly-once semantics, outbox pattern, dead letter handling
6. **Infrastructure as Code**: Not clicking buttons in AWS console - everything codified and version-controlled
7. **Testing at every level**: Unit -> Integration -> Contract -> Architecture -> Mutation -> Performance
8. **Mentorship built-in**: ADRs, runbooks, contributing guide, code comments explaining decisions
