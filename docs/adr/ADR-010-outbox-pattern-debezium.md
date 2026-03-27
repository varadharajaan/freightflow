# ADR-010: Outbox Pattern with Debezium CDC for Reliable Messaging

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow's microservices must reliably publish domain events to Kafka after persisting
state changes to PostgreSQL (ADR-002). The naive approach — writing to the database and then
publishing to Kafka in sequence — is a **dual-write problem**: if the database write succeeds
but the Kafka publish fails (network issue, broker unavailable), the system is left in an
inconsistent state where the database has the new state but downstream consumers never receive
the event. Conversely, if the Kafka publish succeeds but the database commit fails (constraint
violation, deadlock), consumers process an event for a state change that was rolled back.

### Requirements
- **Atomicity** — database state change and event publication must be atomic (all-or-nothing)
- **At-least-once delivery** — every committed database change must eventually produce a
  corresponding Kafka event, even in the face of application crashes or network failures
- **Ordering** — events for the same aggregate (e.g., booking) must be published in the
  order they were committed
- **Low latency** — events should appear on Kafka within seconds of the database commit
- **No data loss** — events must not be lost even during application or infrastructure failures
- **Dead letter handling** — events that repeatedly fail to process must be quarantined
  for manual investigation

### Options Considered
1. **Dual-Write (DB + Kafka in application code)** - Write to PostgreSQL, then publish to
   Kafka in the same service method. No additional infrastructure. However, this is
   fundamentally flawed: there is no atomicity between two different systems. If the app
   crashes between the DB commit and the Kafka publish, the event is lost. Retrying the
   Kafka publish on failure can produce duplicates. Ordering is not guaranteed under concurrent
   writes. This is the problem we are solving.
2. **Database Triggers + Polling Publisher** - Use a database trigger to write events to an
   outbox table, then a polling job reads the outbox and publishes to Kafka. Achieves
   atomicity (trigger is in the same transaction). However, polling introduces latency
   (typically seconds), adds load to the database with frequent queries, and the polling
   interval creates a tradeoff between latency and database load.
3. **Transactional Outbox + Application Polling** - Application code writes to both the
   domain table and an outbox table in the same database transaction. A separate polling
   component reads the outbox and publishes to Kafka. Same atomicity benefits as option 2,
   but polling has latency and load tradeoffs.
4. **Transactional Outbox + Debezium CDC** - Application code writes to the domain table
   and an outbox table in the same transaction. Debezium captures changes from the PostgreSQL
   Write-Ahead Log (WAL) in near real-time and publishes them to Kafka. No polling needed.
   Sub-second latency. No additional database load (reads WAL, not tables).

## Decision

We will implement the **Transactional Outbox Pattern** with **Debezium Change Data Capture
(CDC)** for all services that need to reliably publish domain events to Kafka.

### How It Works

```
[Service Code]
    |
    ├── 1. Write to domain table (e.g., bookings)      ─┐
    └── 2. Write to outbox table (outbox_events)        ─┘ Same DB transaction
                                                           (COMMIT or ROLLBACK together)

[Debezium Connector]
    |
    ├── 3. Reads PostgreSQL WAL (logical replication)
    ├── 4. Detects INSERT on outbox_events table
    └── 5. Publishes event to Kafka topic

[Downstream Consumers]
    |
    └── 6. Consume events from Kafka
```

### Outbox Table Schema
Each service's database contains an `outbox_events` table:

```sql
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(255) NOT NULL,    -- e.g., 'Booking', 'Shipment'
    aggregate_id    VARCHAR(255) NOT NULL,    -- e.g., 'BK-2026-00142'
    event_type      VARCHAR(255) NOT NULL,    -- e.g., 'BookingCreatedEvent'
    payload         JSONB NOT NULL,           -- Event payload
    metadata        JSONB,                    -- Correlation ID, causation ID, user ID
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published       BOOLEAN NOT NULL DEFAULT FALSE
);

-- Index for Debezium CDC (WAL-based, but useful for fallback polling)
CREATE INDEX idx_outbox_unpublished ON outbox_events (published, created_at)
    WHERE published = FALSE;
```

### Application Code Pattern
```java
@Transactional
public Booking createBooking(CreateBookingCommand command) {
    // 1. Execute domain logic
    Booking booking = Booking.create(command);
    bookingRepository.save(booking);

    // 2. Write event to outbox (same transaction)
    OutboxEvent event = OutboxEvent.builder()
        .aggregateType("Booking")
        .aggregateId(booking.getId())
        .eventType("BookingCreatedEvent")
        .payload(objectMapper.valueToTree(BookingCreatedEvent.from(booking)))
        .metadata(Map.of(
            "correlationId", MDC.get("correlationId"),
            "userId", SecurityContextHolder.getContext().getAuthentication().getName()
        ))
        .build();
    outboxRepository.save(event);

    // Both writes commit or rollback together
    return booking;
}
```

### Debezium Connector Configuration
Debezium's PostgreSQL connector reads the WAL using logical replication and routes outbox
events to the appropriate Kafka topics:

```json
{
  "name": "freightflow-booking-outbox",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "${BOOKING_DB_HOST}",
    "database.port": "5432",
    "database.user": "debezium_replication",
    "database.password": "${BOOKING_DB_CDC_PASSWORD}",
    "database.dbname": "booking_db",
    "topic.prefix": "freightflow.booking",
    "plugin.name": "pgoutput",
    "slot.name": "booking_outbox_slot",
    "publication.name": "booking_outbox_pub",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.table.field.event.timestamp": "created_at",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "freightflow.booking.events",
    "transforms.outbox.table.expand.json.payload": true,

    "table.include.list": "public.outbox_events",
    "tombstones.on.delete": false,
    "heartbeat.interval.ms": 10000,
    "snapshot.mode": "never"
  }
}
```

Key configuration choices:
- **`plugin.name: pgoutput`** — PostgreSQL's built-in logical replication plugin (no
  extensions required, unlike `wal2json` or `decoderbufs`).
- **`EventRouter` SMT** — Debezium's Outbox Event Router single message transform routes
  events based on the `aggregate_type` field and uses `aggregate_id` as the Kafka message
  key (ensuring ordering per aggregate).
- **`snapshot.mode: never`** — we never snapshot the outbox table; we only capture live
  changes from the WAL.
- **`heartbeat.interval.ms: 10000`** — heartbeat events prevent the replication slot from
  holding WAL segments indefinitely during low-activity periods.

### PostgreSQL WAL Configuration
```sql
-- postgresql.conf
wal_level = logical
max_replication_slots = 4     -- One per service with outbox
max_wal_senders = 4

-- Create publication for outbox table only
CREATE PUBLICATION booking_outbox_pub FOR TABLE outbox_events;

-- Dedicated replication user with minimal privileges
CREATE ROLE debezium_replication WITH REPLICATION LOGIN PASSWORD '...';
GRANT USAGE ON SCHEMA public TO debezium_replication;
GRANT SELECT ON outbox_events TO debezium_replication;
```

### Outbox Table Cleanup
The outbox table grows continuously. We implement cleanup to prevent unbounded growth:
- **Debezium marks events as published** — after the event is captured, a separate scheduled
  job marks events older than the Debezium LSN position as `published = TRUE`.
- **Periodic deletion** — a scheduled job (`@Scheduled(cron = "0 0 2 * * *")`) deletes
  published events older than 7 days.
- **Alternatively**: use PostgreSQL table partitioning on `created_at` with monthly
  partitions and drop old partitions.

```java
@Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
public void cleanupOutbox() {
    int deleted = outboxRepository.deletePublishedOlderThan(
        Instant.now().minus(7, ChronoUnit.DAYS)
    );
    log.info("Cleaned up {} published outbox events", deleted);
}
```

### Dead Letter Handling
When downstream consumers fail to process an event after exhaustive retries:

1. **Consumer retry** — Spring Kafka `DefaultErrorHandler` retries with exponential backoff
   (1s, 2s, 4s, 8s, 16s) for transient errors.
2. **Dead letter topic** — After retry exhaustion, the event is published to a
   `freightflow.{service}.dlq` topic.
3. **DLQ monitoring** — A dashboard monitors DLQ topic lag. Alerts fire when any message
   lands in the DLQ.
4. **Manual replay** — Operations team investigates and replays DLQ messages using a custom
   admin tool that can re-publish to the original topic after the root cause is resolved.

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new TopicPartition(
                record.topic().replace(".events", ".dlq"), 0));

    return new DefaultErrorHandler(recoverer,
        new ExponentialBackOff(1000L, 2.0)  // 1s, 2s, 4s, 8s, 16s
            .withMaxElapsedTime(60000L));     // Give up after 60 seconds
}
```

### Why Outbox over Dual-Write — Summary

| Aspect              | Dual-Write              | Outbox + Debezium CDC           |
|---------------------|-------------------------|---------------------------------|
| Atomicity           | No — two separate writes| Yes — single DB transaction     |
| Event loss risk     | High (crash between writes) | None (WAL captures all commits) |
| Ordering guarantee  | None under concurrency  | WAL order = commit order        |
| Additional latency  | None                    | ~100-500 ms (WAL → Kafka)       |
| Infrastructure      | None                    | Debezium Connect cluster        |
| Database load       | None extra              | Minimal (WAL read, not table scan)|

## Consequences

### Positive
- **Atomic** event publication — events are part of the same database transaction as domain
  state changes; impossible to have a committed DB change without a corresponding event
- **Zero event loss** — even if the application crashes immediately after commit, Debezium
  captures the change from the WAL
- **Correct ordering** — WAL captures changes in commit order, and Kafka message key
  (aggregate_id) ensures per-aggregate ordering
- **Low latency** — CDC from WAL is near real-time (100-500 ms typical), far better than
  polling-based approaches
- **Minimal database overhead** — Debezium reads the WAL stream, not the outbox table;
  no additional SELECT queries against the database
- **Decoupled from Kafka availability** — if Kafka is temporarily unavailable, the outbox
  rows accumulate in the database and Debezium catches up when Kafka recovers (WAL
  retention provides the buffer)

### Negative
- **Infrastructure complexity** — Debezium (Kafka Connect) is another component to deploy,
  monitor, and operate
- **WAL retention** — if Debezium falls behind (long outage, slow consumer), PostgreSQL
  holds WAL segments, consuming disk space; requires monitoring and alerting on replication
  slot lag
- **Outbox table growth** — requires periodic cleanup to prevent unbounded table size
- **Schema coupling** — the outbox table schema and Debezium connector configuration must
  stay in sync; changes require coordinated updates
- **Debezium connector failures** — connector crashes or rebalances can cause temporary
  event delays; requires monitoring connector status
- **PostgreSQL logical replication overhead** — `wal_level=logical` generates slightly
  larger WAL files than `replica` level

### Mitigations
- Deploy Debezium on Kafka Connect with multiple workers for high availability; use
  distributed mode with connector task rebalancing
- Monitor replication slot lag via `pg_stat_replication` and alert when lag exceeds a
  threshold (e.g., 100 MB or 5 minutes)
- Set `max_slot_wal_keep_size` in PostgreSQL 13+ to cap WAL retention and prevent disk
  exhaustion from a stalled replication slot
- Automate outbox table cleanup with scheduled jobs and monitor table size
- Implement a fallback **polling publisher** that activates if Debezium is down — reads
  `outbox_events WHERE published = FALSE` and publishes to Kafka directly (degraded
  latency but maintains delivery guarantee)
- Use Debezium's built-in offset storage in Kafka to survive connector restarts without
  missing events
- Include Debezium connector health checks in the service mesh observability stack
  (Prometheus metrics from Kafka Connect REST API)

## References
- [Chris Richardson - Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Debezium Documentation](https://debezium.io/documentation/)
- [Debezium Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
- [PostgreSQL Logical Replication](https://www.postgresql.org/docs/current/logical-replication.html)
- [Gunnar Morling - Reliable Microservices Data Exchange with the Outbox Pattern](https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/)
- [Martin Kleppmann - Designing Data-Intensive Applications, Ch. 11](https://dataintensive.net/)
