# FreightFlow - Container Shipping & Logistics Platform

[![Build](https://github.com/YOUR_USERNAME/freightflow/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/freightflow/actions)
[![Quality Gate](https://img.shields.io/badge/quality%20gate-passed-brightgreen)](.)
[![Coverage](https://img.shields.io/badge/coverage-%3E80%25-brightgreen)](.)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

> A production-grade, microservices-based container shipping management platform demonstrating
> enterprise Java architecture, distributed systems patterns, and DevOps best practices.

---

## Architecture Overview

```
                                    +------------------+
                                    |   Load Balancer   |
                                    +--------+---------+
                                             |
                                    +--------+---------+
                                    |   API Gateway     |
                                    | (Spring Cloud GW) |
                                    +--------+---------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
           +--------+--------+    +---------+--------+    +---------+--------+
           | Booking Service  |    | Tracking Service  |    | Billing Service  |
           | (CQRS + ES)     |    | (Kafka Streams)   |    | (Saga Pattern)   |
           +--------+--------+    +---------+--------+    +---------+--------+
                    |                        |                        |
                    +----------+-------------+----------+-------------+
                               |                        |
                    +----------+----------+   +---------+--------+
                    |    Apache Kafka      |   |   PostgreSQL     |
                    |  (Event Backbone)    |   | (Per-service DB) |
                    +---------------------+   +------------------+
```

## Modules

| Module | Description | Key Patterns |
|---|---|---|
| `freightflow-bom` | Dependency version management | BOM pattern |
| `freightflow-parent` | Build plugin management | Parent POM |
| `commons-domain` | Shared value objects & domain primitives | DDD Building Blocks |
| `commons-events` | Event contracts & serialization | Schema Registry |
| `commons-security` | JWT, OAuth2 utilities | Security patterns |
| `commons-observability` | Structured logging, metrics, tracing | Observability |
| `commons-testing` | Test fixtures & custom assertions | Test utilities |
| `booking-service` | Booking lifecycle management | CQRS, Event Sourcing |
| `tracking-service` | Real-time container tracking | Kafka Streams, WebSocket |
| `billing-service` | Invoicing & payment processing | Saga, Outbox |
| `vessel-schedule-service` | Vessel routes & schedules | Graph algorithms, Caching |
| `customer-service` | Customer & contract management | RBAC, Multi-tenancy |
| `notification-service` | Multi-channel notifications | Observer, Async |
| `api-gateway` | Request routing & rate limiting | Gateway, Circuit Breaker |

## Tech Stack

> For the **complete 120+ technology breakdown**, see [`TECH_STACK.md`](TECH_STACK.md)

| Layer | Technologies |
|---|---|
| **Language** | Java 21 (Virtual Threads, Records, Sealed Classes, Pattern Matching) |
| **Framework** | Spring Boot 3.3, Spring Framework 6.1, Spring Cloud 2023 |
| **Service Discovery** | Eureka Server/Client, Spring Cloud LoadBalancer |
| **Resilience** | Resilience4j (Circuit Breaker, Bulkhead, Retry, Rate Limiter) |
| **API** | REST (OpenAPI 3.1), gRPC, GraphQL, WebSocket, SSE, HATEOAS |
| **Data** | PostgreSQL 16, Spring Data JPA, Hibernate 6.4, HikariCP, Flyway, QueryDSL |
| **Caching** | Redis 7, Spring Cache, Caffeine, Hibernate L2 Cache |
| **Messaging** | Apache Kafka 3.7, Kafka Streams, Schema Registry, Debezium (CDC) |
| **Security** | Keycloak (OAuth 2.0/OIDC), Spring Security 6, JWT, RBAC |
| **Containers** | Docker (multi-stage, distroless), Jib |
| **Orchestration** | Kubernetes, Helm v3, Kustomize, HPA, PDB, Network Policies |
| **Service Mesh** | Istio, Envoy Proxy, mTLS, Traffic Splitting, Kiali |
| **CI/CD** | GitHub Actions, SonarQube, Trivy, JaCoCo, Checkstyle, SpotBugs, PIT |
| **GitOps** | ArgoCD, ArgoCD ApplicationSet, Argo Rollouts (Canary/Blue-Green) |
| **Cloud (AWS)** | EKS, RDS, MSK, ElastiCache, S3, ALB, Route 53, WAF, Secrets Manager |
| **IaC** | Terraform, Terragrunt, tfsec, Checkov |
| **Observability** | Micrometer, Prometheus, Grafana, OpenTelemetry, Jaeger, ELK Stack, Fluent Bit |
| **Testing** | JUnit 5, Mockito, Testcontainers, ArchUnit, PIT, Gatling, WireMock, REST Assured, Spring Cloud Contract |

## Prerequisites

- Java 21 (GraalVM or Temurin)
- Maven 3.9+ (or use included `mvnw`)
- Docker & Docker Compose
- PostgreSQL 16 (via Docker)
- Apache Kafka (via Docker)

## Quick Start

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/freightflow.git
cd freightflow

# Start infrastructure (PostgreSQL, Kafka, Redis, Keycloak)
docker-compose -f infrastructure/docker/docker-compose.yml up -d

# Build all modules
./mvnw clean install

# Run a specific service
./mvnw -pl booking-service spring-boot:run

# Run all tests
./mvnw verify

# Run with integration tests (requires Docker)
./mvnw verify -P integration-tests
```

## Project Structure

```
freightflow/
|-- freightflow-bom/
|-- freightflow-parent/
|-- freightflow-commons/
|   |-- commons-domain/
|   |-- commons-events/
|   |-- commons-security/
|   |-- commons-observability/
|   |-- commons-testing/
|-- booking-service/
|-- tracking-service/
|-- billing-service/
|-- vessel-schedule-service/
|-- customer-service/
|-- notification-service/
|-- api-gateway/
|-- infrastructure/
|   |-- docker/
|   |-- kubernetes/
|   |-- terraform/
|   |-- monitoring/
|-- docs/
|   |-- architecture/
|   |-- api/
|   |-- adr/
|   |-- runbooks/
|-- .github/
|   |-- workflows/
|   |-- PULL_REQUEST_TEMPLATE.md
|-- pom.xml
```

## Architecture Decisions

All significant architectural decisions are documented as ADRs in [`docs/adr/`](docs/adr/).

| ADR | Decision |
|---|---|
| ADR-001 | Use CQRS with Event Sourcing for Booking Service |
| ADR-002 | Database-per-service with PostgreSQL |
| ADR-003 | Apache Kafka as the event backbone |
| ADR-004 | Saga pattern (orchestration) for cross-service transactions |
| ADR-005 | Virtual Threads for I/O-bound operations |
| ADR-006 | Contract-first API design with OpenAPI 3.1 |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for coding standards, PR guidelines, and development workflow.

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.
