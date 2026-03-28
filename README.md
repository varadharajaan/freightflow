# FreightFlow - Container Shipping & Logistics Platform

[![Build](https://github.com/varadharajaan/freightflow/actions/workflows/ci.yml/badge.svg)](https://github.com/varadharajaan/freightflow/actions)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Kafka-3.7-red)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)](https://www.postgresql.org/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-1.28-326CE5)](https://kubernetes.io/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

> A production-grade, microservices-based container shipping management platform
> built with Java 21, Spring Boot 3, Apache Kafka, and deployed on Kubernetes
> with Istio service mesh and ArgoCD GitOps.

---

## What is FreightFlow?

FreightFlow is a **distributed platform for managing container shipping operations** - from booking cargo shipments across global trade routes, to real-time container tracking, invoicing, and vessel schedule management.

It demonstrates how to build **enterprise-grade microservices** with:
- Event-driven architecture using **Apache Kafka**
- **CQRS and Event Sourcing** for complex domain workflows
- **Saga pattern** for distributed transactions across services
- **Java 21** features (Virtual Threads, Records, Sealed Classes, Pattern Matching)
- Full **observability** (metrics, logging, distributed tracing)
- **Kubernetes** deployment with **Istio** service mesh
- **GitOps** continuous delivery with **ArgoCD**

---

## Architecture Overview

```
                                    +------------------+
                                    |   ALB / Ingress   |
                                    +--------+---------+
                                             |
                                    +--------+---------+
                                    |   API Gateway     |
                                    | (Spring Cloud GW) |
                                    +--------+---------+
                                             |
          +---------------+------------------+------------------+
          |               |                  |                  |
  +-------+------+ +------+-------+ +-------+------+ +--------+--------+
  |  Booking     | |  Tracking    | |  Billing     | | Vessel Schedule  |
  |  Service     | |  Service     | |  Service     | | Service          |
  |  (CQRS+ES)   | | (Kafka       | |  (Saga       | | (Graph+Cache)    |
  |              | |  Streams)    | |   Pattern)   | |                  |
  +---------+----+ +------+-------+ +------+-------+ +--------+--------+
            |             |                |                   |
  +---------+----+ +------+-------+        |                   |
  |  Customer    | | Notification |        |                   |
  |  Service     | | Service      |        |                   |
  |  (RBAC)      | | (Async)      |        |                   |
  +---------+----+ +------+-------+        |                   |
            |             |                |                   |
    +-------+-------------+----------------+-------------------+
    |                          |                               |
    |    +------------------+  |  +-----------------+          |
    |    |  Apache Kafka    |  |  |  Redis          |          |
    |    |  (Event Backbone)|  |  |  (L2 Cache)     |          |
    |    +------------------+  |  +-----------------+          |
    |                          |                               |
    |    +------------------+  |  +-----------------+          |
    |    |  PostgreSQL      |  |  |  Keycloak       |          |
    |    |  (Per-service DB)|  |  |  (OAuth 2.0)    |          |
    |    +------------------+  |  +-----------------+          |
    +-------+------------------+-------------------------------+
            |
    +-------+----------+    +-----------+    +----------+
    | Eureka           |    | Config    |    | Istio    |
    | (Discovery)      |    | Server    |    | (Mesh)   |
    +------------------+    +-----------+    +----------+
```

---

## Modules

| Module | Description | Key Patterns |
|---|---|---|
| `freightflow-bom` | Dependency version management | BOM pattern |
| `freightflow-parent` | Build plugin management | Parent POM |
| `commons-domain` | Shared value objects & domain primitives | DDD Building Blocks |
| `commons-events` | Event contracts & Avro schemas | Schema Registry |
| `commons-security` | JWT, OAuth2 utilities | Security patterns |
| `commons-observability` | Structured logging, metrics, tracing | Three Pillars |
| `commons-testing` | Test fixtures & custom assertions | Test utilities |
| `commons-exception` | Global exception handling, RFC 7807 Problem Details | Sealed exception hierarchy |
| `booking-service` | Booking lifecycle management | CQRS, Event Sourcing |
| `tracking-service` | Real-time container tracking (Planned) | Kafka Streams, WebSocket |
| `billing-service` | Invoicing & payment processing (Planned) | Saga, Outbox |
| `vessel-schedule-service` | Vessel routes & schedules (Planned) | Graph algorithms, Caching |
| `customer-service` | Customer & contract management (Planned) | RBAC, Multi-tenancy |
| `notification-service` | Multi-channel notifications (Planned) | Observer, Async |
| `api-gateway` | Edge service — routes, rate limits, circuit breaks, JWT validation | Gateway, Circuit Breaker |
| `config-server` | Centralized Git-backed configuration | Spring Cloud Config |
| `discovery-server` | Service registry & discovery dashboard | Eureka Server |

---

## Tech Stack

> Full 150+ technology breakdown: [`TECH_STACK.md`](TECH_STACK.md)

| Layer | Technologies |
|---|---|
| **Language** | Java 21 (Virtual Threads, Records, Sealed Classes, Pattern Matching) |
| **Framework** | Spring Boot 3.3, Spring Framework 6.1, Spring Cloud 2023 |
| **Service Discovery** | Eureka Server/Client, Spring Cloud LoadBalancer |
| **Resilience** | Resilience4j (Circuit Breaker, Bulkhead, Retry, Rate Limiter) |
| **API** | REST (OpenAPI 3.1), gRPC, GraphQL, WebSocket, SSE, HATEOAS |
| **Data** | PostgreSQL 16, Spring Data JPA, Hibernate 6.4, HikariCP, Flyway, QueryDSL |
| **Caching** | Caffeine (L1) + Redis 7 (L2), Spring Cache, Hibernate L2 Cache |
| **Messaging** | Apache Kafka 3.7, Kafka Streams, Schema Registry (Avro), Debezium CDC |
| **Security** | Keycloak (OAuth 2.0/OIDC), Spring Security 6, JWT, RBAC |
| **Containers** | Docker (multi-stage, distroless), Jib (Maven plugin) |
| **Orchestration** | Kubernetes, Helm v3, Kustomize, HPA, PDB, Network Policies |
| **Service Mesh** | Istio, Envoy Proxy, mTLS, Traffic Splitting, Kiali |
| **CI/CD** | GitHub Actions, SonarQube, Trivy, JaCoCo, Checkstyle, SpotBugs, PIT |
| **GitOps** | ArgoCD, Argo Rollouts (Canary/Blue-Green) |
| **Cloud (AWS)** | EKS, RDS, MSK, ElastiCache, S3, ALB, Route 53, WAF, Secrets Manager |
| **IaC** | Terraform, Terragrunt, tfsec, Checkov |
| **Observability** | Micrometer, Prometheus, Grafana, OpenTelemetry, Jaeger, ELK Stack |
| **Testing** | JUnit 5, Mockito, Testcontainers, ArchUnit, PIT, Gatling, WireMock |
| **Utilities** | MapStruct, Vavr, Caffeine, Bucket4j, Shedlock, Redisson |

---

## Getting Started

### Prerequisites

- Java 21 ([Adoptium Temurin](https://adoptium.net/) or [GraalVM](https://www.graalvm.org/))
- Docker & Docker Compose
- Maven 3.9+ (or use included `mvnw`)

### Quick Start

```bash
# Clone
git clone https://github.com/varadharajaan/freightflow.git
cd freightflow

# Start infrastructure (PostgreSQL, Kafka, Redis, Keycloak)
docker-compose -f infrastructure/docker/docker-compose.yml up -d

# Build all modules
./mvnw clean install

# Run booking service (with Virtual Threads)
./mvnw -pl booking-service spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Dspring.threads.virtual.enabled=true"

# Run tests
./mvnw verify

# Run integration tests (uses Testcontainers)
./mvnw verify -P integration-tests
```

> For detailed setup instructions, see [docs/setup/WORKSPACE_SETUP.md](docs/setup/WORKSPACE_SETUP.md)

---

## Documentation

| Document | Description |
|---|---|
| [System Architecture](docs/architecture/SYSTEM_ARCHITECTURE.md) | C4 model, container diagram, component breakdown |
| [Event Flow](docs/architecture/EVENT_FLOW.md) | Kafka topics, event sequences, outbox pattern |
| [API Design Guide](docs/api/API_DESIGN_GUIDE.md) | REST conventions, error handling, pagination |
| [Caching Strategy](docs/caching/CACHING_STRATEGY.md) | L1/L2/L3 caching, stampede prevention |
| [Workspace Setup](docs/setup/WORKSPACE_SETUP.md) | Development environment setup |
| [Tech Stack](TECH_STACK.md) | Complete 150+ technology breakdown |
| [Contributing](CONTRIBUTING.md) | Coding standards, PR guidelines |
| [ADRs](docs/adr/) | Architecture Decision Records |

---

## Architecture Decisions

All significant decisions are documented as ADRs in [`docs/adr/`](docs/adr/).

| ADR | Decision |
|---|---|
| [ADR-001](docs/adr/ADR-001-cqrs-event-sourcing-booking.md) | CQRS with Event Sourcing for Booking Service |
| ADR-002 | Database-per-service with PostgreSQL |
| ADR-003 | Apache Kafka as the event backbone |
| ADR-004 | Saga pattern (orchestration) for distributed transactions |
| ADR-005 | Virtual Threads for I/O-bound operations |
| ADR-006 | Contract-first API design with OpenAPI 3.1 |
| ADR-007 | Multi-level caching strategy (Caffeine + Redis) |
| ADR-008 | Istio service mesh for mTLS and traffic management |
| ADR-009 | ArgoCD GitOps for continuous delivery |
| ADR-010 | Outbox pattern with Debezium CDC for reliable messaging |

---

## Design Patterns Implemented

| Category | Patterns |
|---|---|
| **Creational** | Factory Method, Abstract Factory, Builder, Singleton |
| **Structural** | Adapter, Decorator, Facade, Proxy, Composite |
| **Behavioral** | Strategy, Observer, Command, Chain of Responsibility, Template Method, State |
| **Enterprise** | CQRS, Event Sourcing, Saga, Outbox, Repository, Specification |
| **Resilience** | Circuit Breaker, Bulkhead, Retry, Rate Limiter, Timeout |
| **Integration** | Anti-Corruption Layer, Canonical Data Model, Dead Letter Channel |

---

## Project Structure

```
freightflow/
|-- freightflow-bom/                    # Bill of Materials
|-- freightflow-parent/                 # Parent POM
|-- freightflow-commons/
|   |-- commons-domain/                 # Domain primitives (Money, Weight, ContainerId)
|   |-- commons-events/                 # Avro event schemas
|   |-- commons-security/              # JWT, OAuth2 utilities
|   |-- commons-observability/          # Logging, metrics, tracing
|   |-- commons-testing/               # Test fixtures, Testcontainer configs
|   |-- commons-exception/             # Global exception handling, RFC 7807
|-- booking-service/                    # CQRS + Event Sourcing
|-- tracking-service/                   # Kafka Streams + WebSocket  # (planned)
|-- billing-service/                    # Saga orchestration  # (planned)
|-- vessel-schedule-service/            # Route optimization  # (planned)
|-- customer-service/                   # RBAC + multi-tenancy  # (planned)
|-- notification-service/              # Async notifications  # (planned)
|-- api-gateway/                       # Spring Cloud Gateway (port 8080)
|-- config-server/                     # Spring Cloud Config (Git-backed, port 8888)
|-- discovery-server/                  # Eureka Server (port 8761)
|-- config-repo/                       # Centralized config files (per-service YAMLs)
|-- infrastructure/
|   |-- docker/                        # Dockerfiles, docker-compose.yml
|   |-- kubernetes/                    # K8s manifests, Helm charts
|   |-- terraform/                     # AWS EKS, RDS, MSK, ElastiCache
|   |-- monitoring/                    # Prometheus rules, Grafana dashboards
|-- docs/
|   |-- architecture/                  # C4 diagrams, event flows
|   |-- api/                           # OpenAPI specs, API guide
|   |-- adr/                           # Architecture Decision Records
|   |-- caching/                       # Caching strategy
|   |-- setup/                         # Workspace setup
|   |-- runbooks/                      # Operational runbooks
|-- .github/
|   |-- workflows/                     # CI/CD pipelines
|   |-- PULL_REQUEST_TEMPLATE.md
```

---

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.
