# FreightFlow - System Architecture

## C4 Model Overview

This document describes the FreightFlow architecture using the C4 model
(Context, Container, Component, Code).

---

## Level 1: System Context Diagram

```
                         +---------------------+
                         |    Shipping Line     |
                         |    Operations Team   |
                         +----------+----------+
                                    |
                                    | manages bookings, tracks containers
                                    v
                         +---------------------+
                         |                     |
                         |    FreightFlow      |
                         |    Platform         |
                         |                     |
                         +--+-----+-----+-----+
                            |     |     |
              +-------------+     |     +-------------+
              |                   |                   |
              v                   v                   v
    +---------+--------+ +-------+--------+ +--------+---------+
    | Port Terminal     | | Payment        | | External Vessel  |
    | Systems (TOS)     | | Gateway        | | Tracking (AIS)   |
    +------------------+ +----------------+ +------------------+
    External system      External system     External system
```

### Actors
- **Shipping Line Operations Team**: Books cargo, manages vessel schedules, generates documents
- **Customers (Shippers/Consignees)**: Submit booking requests, track cargo, download documents
- **Port Terminal Systems**: Provide container movement events, berth scheduling
- **Payment Gateway**: Processes invoice payments
- **AIS (Automatic Identification System)**: Provides real-time vessel position data

---

## Level 2: Container Diagram

```
+------------------------------------------------------------------+
|                         FreightFlow Platform                      |
|                                                                   |
|  +-------------+    +------------------+    +-----------------+   |
|  | API Gateway  |<-->| Booking Service  |<-->| Billing Service |   |
|  | (Spring      |    | (CQRS + Event    |    | (Saga Pattern)  |   |
|  |  Cloud GW)   |    |  Sourcing)       |    |                 |   |
|  +------+------+    +--------+---------+    +--------+--------+   |
|         |                    |                       |             |
|         |           +--------+---------+    +--------+--------+   |
|         +---------->| Tracking Service |    | Customer Service |   |
|         |           | (Kafka Streams   |    | (RBAC + Multi-   |   |
|         |           |  + WebSocket)    |    |  tenancy)        |   |
|         |           +--------+---------+    +--------+--------+   |
|         |                    |                       |             |
|         |           +--------+---------+    +--------+--------+   |
|         +---------->| Vessel Schedule  |    | Notification     |   |
|                     | Service (Graph   |    | Service (Kafka   |   |
|                     |  + Caching)      |    |  + Async)        |   |
|                     +--------+---------+    +--------+--------+   |
|                              |                       |             |
|  +---------------------------+---+-------------------+----------+ |
|  |                               |                              | |
|  |  +------------+    +---------+--------+    +-----------+     | |
|  |  | Eureka     |    | Apache Kafka     |    | Redis     |     | |
|  |  | (Discovery)|    | (Event Backbone) |    | (Cache)   |     | |
|  |  +------------+    +------------------+    +-----------+     | |
|  |                               |                              | |
|  |  +------------+    +---------+--------+                      | |
|  |  | Config     |    | PostgreSQL       |                      | |
|  |  | Server     |    | (Per-service DB) |                      | |
|  |  +------------+    +------------------+                      | |
|  +--------------------------------------------------------------+ |
+------------------------------------------------------------------+
```

### Containers

| Container | Technology | Purpose |
|---|---|---|
| **API Gateway** | Spring Cloud Gateway | Request routing, rate limiting, authentication |
| **Booking Service** | Spring Boot 3.3 + Java 21 | Booking lifecycle (CQRS + Event Sourcing) |
| **Tracking Service** | Spring Boot 3.3 + Kafka Streams | Real-time container position tracking |
| **Billing Service** | Spring Boot 3.3 + Java 21 | Invoice generation, payment processing (Saga) |
| **Vessel Schedule Service** | Spring Boot 3.3 + Java 21 | Route management, schedule optimization |
| **Customer Service** | Spring Boot 3.3 + Spring Security | Customer management, RBAC |
| **Notification Service** | Spring Boot 3.3 + Kafka | Multi-channel notifications (email, SMS, webhook) |
| **Eureka** | Spring Cloud Netflix Eureka | Service registration and discovery |
| **Config Server** | Spring Cloud Config | Centralized Git-backed configuration |
| **Apache Kafka** | Kafka 3.7+ | Event backbone, async messaging |
| **PostgreSQL** | PostgreSQL 16 | Relational data (database-per-service) |
| **Redis** | Redis 7+ | Distributed caching, session store, rate limiting |

---

## Level 3: Component Diagram (Booking Service)

```
+----------------------------------------------------------------------+
|                        Booking Service                                |
|                                                                       |
|  +-------------------+     +--------------------+                     |
|  | REST Controller   |     | Kafka Consumer     |                     |
|  | (Inbound Adapter) |     | (Inbound Adapter)  |                     |
|  +--------+----------+     +---------+----------+                     |
|           |                          |                                |
|  +--------v--------------------------v----------+                     |
|  |              Application Layer               |                     |
|  |                                              |                     |
|  |  +------------------+  +------------------+  |                     |
|  |  | Command Handlers |  | Query Handlers   |  |                     |
|  |  | (Write Side)     |  | (Read Side)      |  |                     |
|  |  +--------+---------+  +--------+---------+  |                     |
|  +-----------|------------------------|---------+                     |
|              |                        |                               |
|  +-----------v-----------+ +---------v-----------+                    |
|  |     Domain Layer      | |   Query Model       |                    |
|  |                       | |                      |                    |
|  | +------------------+  | | +----------------+   |                    |
|  | | Booking          |  | | | BookingView    |   |                    |
|  | | Aggregate        |  | | | (Read Model)   |   |                    |
|  | +------------------+  | | +----------------+   |                    |
|  | | BookingEvent     |  | |                      |                    |
|  | | (Domain Events)  |  | |                      |                    |
|  | +------------------+  | |                      |                    |
|  +-----------+-----------+ +---------+-----------+                    |
|              |                       |                                |
|  +-----------v-----------+ +--------v-----------+                     |
|  | Event Store           | | Read DB            |                     |
|  | (PostgreSQL + JSONB)  | | (PostgreSQL View)  |                     |
|  | (Outbound Adapter)    | | (Outbound Adapter) |                     |
|  +-----------------------+ +--------------------+                     |
|              |                                                        |
|  +-----------v-----------+                                            |
|  | Kafka Producer        |                                            |
|  | (Outbound Adapter)    |                                            |
|  +-----------------------+                                            |
+----------------------------------------------------------------------+
```

### Components (Booking Service)

| Component | Responsibility |
|---|---|
| **REST Controller** | HTTP API for booking CRUD, validation, response mapping |
| **Kafka Consumer (Planned)** | Will listen for external events (payment confirmed, vessel departed) |
| **Command Handlers** | Process write operations (CreateBooking, ConfirmBooking, CancelBooking) |
| **Query Handlers** | Process read operations (GetBooking, SearchBookings, GetBookingHistory) |
| **Booking Aggregate** | Domain logic, business rules, invariant enforcement |
| **Domain Events** | BookingCreated, BookingConfirmed, BookingCancelled, CargoLoaded |
| **Event Store** | Persists domain events to PostgreSQL with JSONB payloads |
| **Read Model** | Materialized projections optimized for queries |
| **Projection Updater** | Materializes domain events into read model projections (CQRS bridge) via @EventListener |
| **Event Publisher** | Publishes domain events (currently via Spring ApplicationEvent, Kafka planned) |

---

## Communication Patterns

### Synchronous (Request-Response)
```
Client --> API Gateway --> Service (via Spring Cloud LoadBalancer)
```
- Used for: Queries, commands requiring immediate response
- Protocol: HTTP/REST, gRPC (internal)
- Discovery: Eureka + Spring Cloud LoadBalancer

### Asynchronous (Event-Driven)
```
Service A --> Kafka Topic --> Service B
```
- Used for: State changes, cross-service coordination
- Protocol: Apache Kafka with Avro serialization
- Patterns: Event Notification, Event-Carried State Transfer

### Saga (Distributed Transaction)
```
Booking Service --[BookingCreated]--> Kafka
    --> Billing Service --[InvoiceGenerated]--> Kafka
        --> Notification Service --[NotificationSent]--> Kafka
            --> Booking Service (saga completes)
```
- Pattern: Choreography-based saga with compensating transactions
- Failure: Each step publishes a failure event triggering compensation

---

## Data Architecture

### Database-per-Service
Each service owns its database. No shared databases.

| Service | Database | Schema Strategy |
|---|---|---|
| Booking Service | `freightflow_booking` | Event store (JSONB) + read projections |
| Tracking Service | `freightflow_tracking` | Time-series partitioned tables |
| Billing Service | `freightflow_billing` | Double-entry ledger |
| Vessel Schedule | `freightflow_vessel` | Graph adjacency + schedule tables |
| Customer Service | `freightflow_customer` | Multi-tenant with row-level security |

### Event Store Schema
```sql
CREATE TABLE booking_events (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_data      JSONB NOT NULL,
    metadata        JSONB,
    version         BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    UNIQUE (aggregate_id, version)
) PARTITION BY RANGE (created_at);
```

### Read Model Projection Schema
```sql
CREATE TABLE booking_projections (
    booking_id               UUID            PRIMARY KEY,
    customer_id              UUID            NOT NULL,
    status                   VARCHAR(20)     NOT NULL,
    origin_port              VARCHAR(10)     NOT NULL,
    destination_port         VARCHAR(10)     NOT NULL,
    container_type           VARCHAR(20)     NOT NULL,
    container_count          INTEGER         NOT NULL,
    commodity_description    VARCHAR(500),
    voyage_id                UUID,
    last_event_version       BIGINT          NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ     NOT NULL,
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    sequence_number          BIGSERIAL       NOT NULL
);
```

---

## Deployment Architecture (AWS)

```
+------------------------------------------------------------------+
|                         AWS VPC                                    |
|                                                                    |
|  +----------------------------+  +-----------------------------+   |
|  |     Public Subnet          |  |     Private Subnet          |   |
|  |                            |  |                             |   |
|  |  +--------+  +---------+  |  |  +-----------------------+  |   |
|  |  |  ALB   |  | NAT GW  |  |  |  |    EKS Cluster        |  |   |
|  |  +---+----+  +---------+  |  |  |                       |  |   |
|  |      |                    |  |  |  +--+ +--+ +--+ +--+  |  |   |
|  +------|--------------------+  |  |  |P1| |P2| |P3| |P4|  |  |   |
|         |                       |  |  +--+ +--+ +--+ +--+  |  |   |
|         +-----------------------+->|                       |  |   |
|                                 |  |  Istio Service Mesh   |  |   |
|                                 |  +-----------------------+  |   |
|                                 |                             |   |
|                                 |  +-----------+ +---------+  |   |
|                                 |  | RDS       | | MSK     |  |   |
|                                 |  | PostgreSQL| | Kafka   |  |   |
|                                 |  | Multi-AZ  | |         |  |   |
|                                 |  +-----------+ +---------+  |   |
|                                 |                             |   |
|                                 |  +-----------+              |   |
|                                 |  |ElastiCache|              |   |
|                                 |  | Redis     |              |   |
|                                 |  +-----------+              |   |
|                                 +-----------------------------+   |
+------------------------------------------------------------------+
```

### AWS Resources
| Resource | Service | Configuration |
|---|---|---|
| **EKS** | Container Orchestration | 3 AZs, managed node groups, Istio service mesh |
| **RDS PostgreSQL** | Database | Multi-AZ, encrypted, automated backups |
| **MSK** | Kafka | 3 brokers, 3 AZs, encrypted in-transit |
| **ElastiCache** | Redis | Cluster mode, multi-AZ, encrypted |
| **ALB** | Load Balancer | SSL termination, WAF integration |
| **S3** | Object Storage | Documents, backups, Terraform state |
| **Route 53** | DNS | Health-checked routing |
| **Secrets Manager** | Secrets | Auto-rotation, IAM-based access |
