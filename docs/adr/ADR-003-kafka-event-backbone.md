# ADR-003: Apache Kafka as Event Backbone

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow's microservices architecture requires a reliable, high-throughput event streaming
platform to serve as the backbone for inter-service communication. Events drive critical
workflows: booking lifecycle changes propagate to tracking, billing, and notification services;
tracking telemetry feeds real-time dashboards; and billing events trigger invoicing workflows.

### Requirements
- High throughput — Tracking service alone is projected to produce 50,000+ events/second
  during peak hours across all active shipments
- Durability — events must survive broker failures without data loss
- Ordering guarantees — booking events for a given booking must be processed in order
- Event replay — ability to reprocess historical events for new consumers or bug fixes
- Schema evolution — event schemas will change over time without breaking consumers
- Exactly-once processing semantics for financial events (billing, payments)
- Multi-datacenter replication for disaster recovery

### Options Considered
1. **RabbitMQ** - Mature, feature-rich message broker with flexible routing (exchanges,
   bindings). Excellent for task queues and point-to-point messaging. However, it is
   fundamentally a message *broker* (messages are consumed and deleted), not an event *log*.
   No native event replay. Throughput caps around 20,000-30,000 msg/s per node. Scaling
   requires complex clustering with mirrored queues that have known split-brain issues.
2. **ActiveMQ / ActiveMQ Artemis** - JMS-compliant, strong Java ecosystem integration.
   However, similar limitations to RabbitMQ: no event log semantics, limited horizontal
   scalability, and ActiveMQ Classic has known performance bottlenecks at high throughput.
3. **Apache Kafka** - Distributed event streaming platform designed as a commit log. Native
   partitioning, replication, and consumer groups. Event retention with configurable TTL or
   infinite retention. Exactly-once semantics (EOS) since Kafka 0.11+. Schema Registry for
   governed schema evolution. Proven at scale (LinkedIn, Netflix, Uber).
4. **Apache Pulsar** - Multi-tenant, geo-replicated messaging with tiered storage. Strong
   feature set but smaller community, fewer production war stories, and more complex
   operational model (requires BookKeeper + ZooKeeper).

## Decision

We will use **Apache Kafka** (KRaft mode, ZooKeeper-free) as the central event backbone
for all inter-service asynchronous communication in FreightFlow.

### Topic Design
We adopt a domain-driven topic naming convention:

```
freightflow.{domain}.{event-type}
```

| Topic Name                              | Partitions | Retention | Purpose                             |
|-----------------------------------------|------------|-----------|-------------------------------------|
| `freightflow.booking.events`            | 12         | 30 days   | Booking lifecycle events            |
| `freightflow.booking.commands`          | 6          | 7 days    | Booking command requests            |
| `freightflow.tracking.telemetry`        | 24         | 14 days   | GPS/IoT telemetry data              |
| `freightflow.tracking.events`           | 12         | 30 days   | Tracking status change events       |
| `freightflow.billing.events`            | 6          | 90 days   | Billing/invoicing events            |
| `freightflow.billing.dlq`              | 3          | infinite  | Dead letter queue for billing       |
| `freightflow.notification.commands`     | 6          | 3 days    | Notification dispatch commands      |
| `freightflow.route.optimization.events` | 6          | 14 days   | Route optimization results          |

- **Partition key strategy**: Booking events are keyed by `bookingId` to guarantee ordering
  per booking. Tracking telemetry is keyed by `shipmentId`. Billing events are keyed by
  `invoiceId`.
- **Partition count rationale**: Set to at least 2× the maximum expected consumer instances
  to allow headroom for scaling consumer groups.

### Exactly-Once Semantics (EOS)
- Kafka producers are configured with `enable.idempotence=true` and `acks=all`.
- Transactional producers (`transactional.id` per service instance) wrap produce-and-consume
  cycles in atomic transactions for billing event processing.
- Consumer isolation level set to `read_committed` for billing consumers to skip
  uncommitted/aborted messages.
- Spring Kafka's `KafkaTransactionManager` integrates with Spring's `@Transactional` to
  coordinate Kafka transactions with database transactions.

### Schema Registry with Avro
- **Confluent Schema Registry** manages all event schemas.
- Schemas are defined in **Apache Avro** format for compact binary serialization and
  built-in schema evolution support.
- Schema compatibility mode: **BACKWARD** (new schema can read data written by the old schema).
  This allows consumers to upgrade before producers.
- Schemas are stored in the Maven project under `src/main/avro/` and compiled at build time
  via the `avro-maven-plugin`.
- Schema ID is embedded in each Kafka message header, enabling consumers to deserialize
  without prior knowledge of the schema version.

### Broker Configuration (Production)
- **Replication factor**: 3 (across 3 availability zones)
- **Min in-sync replicas**: 2 (`min.insync.replicas=2`)
- **Unclean leader election**: Disabled (`unclean.leader.election.enable=false`) to prevent
  data loss
- **Log compaction**: Enabled on entity-state topics (e.g., booking current state snapshots)
- **Compression**: LZ4 for optimal throughput-to-compression ratio

### Spring Kafka Integration
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
      isolation-level: read_committed
      properties:
        max.poll.records: 500
        max.poll.interval.ms: 300000
```

## Consequences

### Positive
- High throughput (millions of events/second) with horizontal scalability by adding brokers
  and partitions
- Durable, append-only commit log enables event replay for new consumers, debugging, or
  reprocessing after bug fixes
- Strong ordering guarantees within partitions satisfy per-booking event ordering requirements
- Exactly-once semantics for financial events prevent duplicate billing or payment processing
- Schema Registry + Avro provides governed schema evolution, preventing breaking changes
  from reaching production
- KRaft mode eliminates ZooKeeper dependency, reducing operational complexity
- Rich ecosystem — Kafka Connect, Kafka Streams, ksqlDB for future stream processing needs

### Negative
- Kafka has a steeper learning curve compared to RabbitMQ for teams new to event streaming
- Operational complexity — managing brokers, partitions, replication, and consumer lag
  requires expertise
- Schema Registry adds another infrastructure component to deploy and monitor
- Avro requires a compile-time code generation step; schema changes require rebuilds
- Debugging Kafka-based flows is harder than synchronous REST calls — requires distributed
  tracing (correlation IDs, OpenTelemetry)
- Consumer lag can grow silently if consumers are underprovisioned

### Mitigations
- Provide team training on Kafka fundamentals and Spring Kafka patterns
- Use Confluent Cloud or Amazon MSK in production to offload broker management
- Implement comprehensive consumer lag monitoring via Prometheus (`kafka_consumer_lag`)
  with alerting thresholds per consumer group
- Integrate OpenTelemetry tracing with Kafka headers to enable end-to-end distributed
  tracing across event-driven flows
- Build a shared `freightflow-kafka-starter` library encapsulating producer/consumer
  configuration, serialization, error handling, and dead letter topic routing
- Use Kafka UI (Redpanda Console or AKHQ) for developer-friendly topic inspection and
  consumer group management

## References
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/)
- [Kafka Exactly-Once Semantics](https://www.confluent.io/blog/exactly-once-semantics-are-possible-heres-how-apache-kafka-does-it/)
- [Spring for Apache Kafka](https://docs.spring.io/spring-kafka/reference/)
- [KRaft: Apache Kafka Without ZooKeeper](https://developer.confluent.io/learn/kraft/)
- [Martin Kleppmann - Designing Data-Intensive Applications, Ch. 11](https://dataintensive.net/)
