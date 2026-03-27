# FreightFlow - Event Flow Architecture

## Event-Driven Communication Overview

All inter-service communication in FreightFlow follows an event-driven architecture
using Apache Kafka as the event backbone. Services communicate through domain events,
ensuring loose coupling and independent deployability.

---

## Booking Flow (Happy Path)

```
Customer                API Gateway           Booking Service        Billing Service       Notification Service
   |                        |                        |                      |                       |
   |-- POST /bookings ----> |                        |                      |                       |
   |                        |-- CreateBookingCmd --> |                      |                       |
   |                        |                        |                      |                       |
   |                        |                        |-- [Validate] ------->|                       |
   |                        |                        |-- [Persist Event] -->|                       |
   |                        |                        |                      |                       |
   |                        |<-- 202 Accepted ------ |                      |                       |
   |<-- 202 Accepted -------|                        |                      |                       |
   |                        |                        |                      |                       |
   |                        |            Kafka: booking.events              |                       |
   |                        |                        |--BookingCreated----> |                       |
   |                        |                        |--BookingCreated----------------------------> |
   |                        |                        |                      |                       |
   |                        |                        |                      |-- Generate Invoice    |
   |                        |                        |                      |-- [Persist]           |
   |                        |                        |                      |                       |
   |                        |                        |    Kafka: billing.events                     |
   |                        |                        |<---InvoiceGenerated--|                       |
   |                        |                        |                      |                       |
   |                        |                        |-- [Update Booking    |                       |
   |                        |                        |    State: INVOICED]  |                       |
   |                        |                        |                      |                       |
   |                        |                        |                      |  Kafka: billing.events|
   |                        |                        |                      |---InvoiceGenerated--->|
   |                        |                        |                      |                       |
   |                        |                        |                      |          [Send Email] |
   |<----- Email: Invoice Created ------------------------------------------------------------ |
```

---

## Booking Cancellation Flow (Compensating Transaction)

```
Customer               Booking Service        Billing Service       Notification Service
   |                        |                      |                       |
   |-- DELETE /bookings/123 |                      |                       |
   |                        |                      |                       |
   |                        |-- [Validate State]   |                       |
   |                        |-- [Persist Event]    |                       |
   |                        |                      |                       |
   |                 Kafka: booking.events         |                       |
   |                        |--BookingCancelled-->|                        |
   |                        |--BookingCancelled------------------------->|
   |                        |                      |                       |
   |                        |                      |-- [Credit Note]       |
   |                        |                      |-- [Refund if paid]    |
   |                        |                      |                       |
   |                  Kafka: billing.events        |                       |
   |                        |<--RefundIssued-------|                       |
   |                        |                      |---RefundIssued------->|
   |                        |                      |                       |
   |                        |                      |          [Send Email] |
   |<----- Email: Booking Cancelled + Refund --------------------------------------------------|
```

---

## Kafka Topic Design

| Topic | Partitions | Retention | Key | Value | Consumers |
|---|---|---|---|---|---|
| `booking.events` | 12 | 30 days | `bookingId` | Avro (BookingEvent) | Billing, Tracking, Notification |
| `booking.commands` | 6 | 7 days | `bookingId` | Avro (BookingCommand) | Booking Service |
| `billing.events` | 6 | 30 days | `invoiceId` | Avro (BillingEvent) | Booking, Notification |
| `tracking.events` | 24 | 14 days | `containerId` | Avro (TrackingEvent) | Booking, Notification |
| `vessel.events` | 6 | 30 days | `vesselId` | Avro (VesselEvent) | Tracking, Booking |
| `notification.commands` | 6 | 3 days | `recipientId` | Avro (NotificationCmd) | Notification Service |
| `*.dlq` (Dead Letter) | 3 | 90 days | original key | original + error metadata | Alert Service |

### Partitioning Strategy
- **Booking events**: Partitioned by `bookingId` to ensure ordering per booking
- **Tracking events**: Partitioned by `containerId` (24 partitions for high throughput)
- **Billing events**: Partitioned by `invoiceId`

### Consumer Group Design
```
booking-service-group     -> booking.commands, billing.events, vessel.events
billing-service-group     -> booking.events
tracking-service-group    -> booking.events, vessel.events
notification-service-group -> booking.events, billing.events, tracking.events
```

---

## Event Schema (Avro)

### BookingCreated Event
```json
{
  "namespace": "com.freightflow.booking.events",
  "type": "record",
  "name": "BookingCreated",
  "fields": [
    {"name": "eventId", "type": "string"},
    {"name": "bookingId", "type": "string"},
    {"name": "customerId", "type": "string"},
    {"name": "origin", "type": "string"},
    {"name": "destination", "type": "string"},
    {"name": "containerType", "type": {"type": "enum", "name": "ContainerType", "symbols": ["DRY_20", "DRY_40", "REEFER_20", "REEFER_40"]}},
    {"name": "quantity", "type": "int"},
    {"name": "requestedDepartureDate", "type": "string"},
    {"name": "commodityDescription", "type": "string"},
    {"name": "weight", "type": {"type": "record", "name": "Weight", "fields": [
      {"name": "value", "type": "double"},
      {"name": "unit", "type": {"type": "enum", "name": "WeightUnit", "symbols": ["KG", "LBS"]}}
    ]}},
    {"name": "occurredAt", "type": "string"},
    {"name": "version", "type": "int"}
  ]
}
```

---

## Outbox Pattern (Transactional Messaging)

To ensure exactly-once delivery between the database and Kafka:

```
1. Business transaction writes to domain table + outbox table (same DB transaction)
2. Debezium CDC reads outbox table changes from PostgreSQL WAL
3. Debezium publishes to Kafka topic
4. Outbox cleaner job removes processed entries

+-------------------+      +----------+      +---------+      +-------+
| Booking Service   | ---> | Outbox   | ---> |Debezium | ---> | Kafka |
| (JPA Transaction) |      | Table    |      | CDC     |      |       |
+-------------------+      +----------+      +---------+      +-------+
        |                        |
        |   Same DB Transaction  |
        +------------------------+
```

### Outbox Table Schema
```sql
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at)
    WHERE processed = FALSE;
```

---

## Dead Letter Queue (DLQ) Strategy

```
Consumer fails to process message (after 3 retries with exponential backoff)
    |
    v
Message sent to DLQ topic (e.g., booking.events.dlq)
    |
    v
DLQ contains: original message + error details + retry count + timestamp
    |
    v
Alert sent to monitoring (Prometheus alert -> PagerDuty)
    |
    v
Manual investigation OR automated reprocessing after fix
```

### Retry Configuration
```yaml
spring:
  kafka:
    consumer:
      properties:
        max.poll.records: 100
        max.poll.interval.ms: 300000
    listener:
      concurrency: 3
      ack-mode: MANUAL

# Resilience4j retry for consumer
resilience4j:
  retry:
    instances:
      kafka-consumer:
        max-attempts: 3
        wait-duration: 2s
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.net.SocketTimeoutException
          - org.springframework.dao.TransientDataAccessException
```
