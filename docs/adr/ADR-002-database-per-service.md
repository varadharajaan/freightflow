# ADR-002: Database-per-Service with PostgreSQL

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow is composed of multiple bounded contexts (Booking, Tracking, Billing, Notification,
Route Optimization) each owning distinct domain models. We need to decide on the database
ownership strategy — whether services share a single database or each service owns its
dedicated database instance.

### Requirements
- Strong domain boundary enforcement between services
- Independent deployability of each service without schema coupling
- Ability to scale storage independently per service based on load characteristics
- Resilience — a database failure in one service must not cascade to others
- Support for polyglot persistence in the future (e.g., TimescaleDB for tracking telemetry)
- Compliance with data sovereignty requirements for certain freight corridors

### Options Considered
1. **Shared Database** - All services connect to a single PostgreSQL instance with separate schemas.
   Simple to set up and operate. However, creates tight coupling: schema migrations become
   coordinated events, a slow query in billing can starve booking of connections, and
   independent scaling is impossible.
2. **Shared Database with Schema-per-Service** - Logical separation via PostgreSQL schemas but
   on a single cluster. Improves logical boundaries but still shares connection pools, I/O
   bandwidth, and operational blast radius.
3. **Database-per-Service** - Each microservice owns its dedicated PostgreSQL instance. Full
   physical isolation. Enables independent scaling, tuning, and lifecycle management.
4. **Polyglot Persistence** - Each service picks the best database for its workload. Maximizes
   fit but dramatically increases operational complexity on day one.

## Decision

We will adopt the **Database-per-Service** pattern with **PostgreSQL** as the default database
engine for all services. Each service will have its own dedicated PostgreSQL instance managed
as a Kubernetes StatefulSet (development/staging) or Amazon RDS/Aurora instance (production).

### Schema Management — Flyway
- All schema migrations are managed with **Flyway** embedded in each service's Spring Boot
  application (`spring.flyway.enabled=true`).
- Migrations follow the naming convention `V{version}__{description}.sql` (e.g.,
  `V001__create_booking_table.sql`).
- Repeatable migrations (`R__`) are used for views and stored functions.
- Flyway runs automatically on application startup, ensuring the schema is always consistent
  with the deployed code version.
- A `flyway_schema_history` table in each database provides full migration audit trail.
- **Baseline migrations** are used when onboarding an existing database:
  `spring.flyway.baseline-on-migrate=true`.

### Connection Pool — HikariCP Tuning
- HikariCP is the default connection pool in Spring Boot. We apply service-specific tuning
  based on workload characteristics:

| Property                          | Booking Service | Tracking Service | Billing Service |
|-----------------------------------|-----------------|------------------|-----------------|
| `maximumPoolSize`                 | 20              | 30               | 15              |
| `minimumIdle`                     | 5               | 10               | 5               |
| `connectionTimeout`              | 30000 ms        | 20000 ms         | 30000 ms        |
| `idleTimeout`                     | 600000 ms       | 600000 ms        | 600000 ms       |
| `maxLifetime`                     | 1800000 ms      | 1800000 ms       | 1800000 ms      |
| `leakDetectionThreshold`         | 60000 ms        | 30000 ms         | 60000 ms        |

- **Pool sizing rationale**: We follow the formula
  `connections = ((2 * CPU cores) + effective_spindle_count)` as a baseline, adjusted
  by observed query latency distributions.
- `maxLifetime` is set 30 seconds shorter than PostgreSQL's `idle_in_transaction_session_timeout`
  to prevent stale connections.
- `leakDetectionThreshold` enables proactive detection of connection leaks in development
  and staging; logs a stack trace when a connection is held beyond the threshold.

### Cross-Service Data Access
- Services **never** access another service's database directly.
- Data that spans service boundaries is obtained via:
  - **Synchronous API calls** for real-time queries (with Circuit Breaker via Resilience4j).
  - **Kafka event consumption** for building local read projections of foreign data.
  - **Saga pattern** (see ADR-004) for distributed transactions.

### Operational Considerations
- Each database is backed up independently with point-in-time recovery (PITR) enabled.
- Monitoring via `pg_stat_statements` and Prometheus `postgres_exporter` per instance.
- Alerting on connection pool saturation (`hikaricp_connections_active` approaching
  `hikaricp_connections_max`).

## Consequences

### Positive
- True service autonomy — teams can evolve schemas independently without cross-team coordination
- Fault isolation — a database outage in Tracking does not impact Booking or Billing
- Independent scaling — Tracking's write-heavy telemetry workload can scale storage/IOPS
  without over-provisioning for Billing's modest read-heavy workload
- Enables future polyglot persistence (e.g., migrating Tracking to TimescaleDB) without
  affecting other services
- HikariCP tuning per service allows optimal connection utilization matched to workload
- Flyway ensures reproducible, version-controlled schema evolution tied to deployment artifacts

### Negative
- Higher infrastructure cost — multiple database instances instead of one
- Distributed queries across services require API composition (no cross-database JOINs)
- Data consistency is eventually consistent across service boundaries
- Operational overhead — more databases to monitor, backup, patch, and tune
- Reporting/analytics across services requires a dedicated data pipeline (ETL/CDC to a
  data warehouse)

### Mitigations
- Use Amazon RDS or Aurora with Multi-AZ for production to offload patching, backups,
  and failover to the managed service
- Implement a centralized observability stack (Prometheus + Grafana dashboards) with
  unified alerting for all database instances
- Build a CDC pipeline (Debezium — see ADR-010) to replicate events into a data warehouse
  (e.g., Snowflake or Redshift) for cross-service analytics
- Standardize HikariCP and Flyway configuration via a shared Spring Boot starter library
  (`freightflow-db-starter`) to reduce per-service boilerplate
- Use Terraform modules to templatize database provisioning and ensure consistent
  configuration across environments

## References
- [Chris Richardson - Database per Service](https://microservices.io/patterns/data/database-per-service.html)
- [HikariCP - About Pool Sizing](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)
- [Flyway Documentation](https://documentation.red-gate.com/fd)
- [PostgreSQL Connection Pooling Best Practices](https://www.postgresql.org/docs/current/runtime-config-connection.html)
- [AWS Aurora PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.AuroraPostgreSQL.html)
