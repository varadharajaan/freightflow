# Saga Orchestration Pattern — FreightFlow

## Overview

FreightFlow uses the **Saga Orchestration** pattern to coordinate distributed transactions across multiple microservices. A centralized **orchestrator** drives the saga forward step by step, and executes **compensating transactions** in reverse order if any step fails.

### Why Orchestration (Not Choreography)?

| Aspect | Orchestration (Chosen) | Choreography |
|---|---|---|
| **Control flow** | Centralized — orchestrator drives all steps | Decentralized — each service listens for events and reacts |
| **Visibility** | Full saga state visible in one place | State scattered across services |
| **Debugging** | Single execution log per saga | Requires correlating events across services |
| **Coupling** | Orchestrator knows all participants | Services know about each other's events |
| **Complexity** | Grows linearly with steps | Grows exponentially with event chains |
| **Compensation** | Explicit, ordered, testable | Implicit, hard to verify all paths |

**Decision:** FreightFlow chose orchestration because:
1. **Booking confirmation** spans 4 services with strict ordering requirements
2. **Compensation logic** must execute in precise reverse order
3. **Observability** is critical — operators need to see saga status at a glance
4. **Testing** is simpler — the orchestrator can be unit-tested with mocked services

> See also: **ADR-004** for the architectural decision record.

---

## Booking Confirmation Saga

### Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                        BOOKING CONFIRMATION SAGA                                      │
│                                                                                      │
│  Client Request                                                                      │
│       │                                                                              │
│       ▼                                                                              │
│  ┌─────────────────────┐                                                             │
│  │ Idempotency Check   │──── Key exists? ──→ Return existing result                  │
│  └──────────┬──────────┘                                                             │
│             │ New key                                                                │
│             ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────────────┐      │
│  │ Step 1: CONFIRM_BOOKING (booking-service)                                  │      │
│  │   Action: Transition booking DRAFT → CONFIRMED, assign voyage              │      │
│  │   Compensation: Cancel booking with reason "Saga compensation"             │      │
│  └──────────┬──────────────────────────────────────────────────────────────────┘      │
│             │                                                                        │
│             ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────────────┐      │
│  │ Step 2: RESERVE_CAPACITY (vessel-schedule-service)                         │      │
│  │   Action: Reserve TEU capacity on the assigned voyage                      │      │
│  │   Compensation: Release reserved capacity                                  │      │
│  └──────────┬──────────────────────────────────────────────────────────────────┘      │
│             │                                                                        │
│             ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────────────┐      │
│  │ Step 3: GENERATE_INVOICE (billing-service)                                 │      │
│  │   Action: Generate invoice for the confirmed booking                       │      │
│  │   Compensation: Cancel invoice (via BookingCancelled event)                │      │
│  └──────────┬──────────────────────────────────────────────────────────────────┘      │
│             │                                                                        │
│             ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────────────┐      │
│  │ Step 4: SEND_NOTIFICATION (notification-service)                           │      │
│  │   Action: Send booking confirmation notification                           │      │
│  │   Compensation: NONE (fire-and-forget)                                     │      │
│  └──────────┬──────────────────────────────────────────────────────────────────┘      │
│             │                                                                        │
│             ▼                                                                        │
│       SAGA COMPLETED                                                                 │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

### Failure Scenarios and Compensation Matrix

| Failure Point | Steps to Compensate | Compensation Order |
|---|---|---|
| Step 1: Confirm Booking fails | *None* — nothing to undo | N/A |
| Step 2: Reserve Capacity fails | Cancel Booking | Step 1 |
| Step 3: Generate Invoice fails | Release Capacity → Cancel Booking | Step 2 → Step 1 |
| Step 4: Send Notification fails | *None* — fire-and-forget, saga completes | N/A |

### Compensation Flow (on Step 3 failure)

```
Step 3 FAILS
    │
    ▼
┌────────────────────────────────┐
│ Compensation: GENERATE_INVOICE │  (handled via BookingCancelled event)
└──────────────┬─────────────────┘
               │
               ▼
┌────────────────────────────────┐
│ Compensation: RESERVE_CAPACITY │  vesselClient.releaseCapacity()
└──────────────┬─────────────────┘
               │
               ▼
┌────────────────────────────────┐
│ Compensation: CONFIRM_BOOKING  │  bookingService.cancelBooking()
└──────────────┬─────────────────┘
               │
               ▼
         SAGA FAILED
```

---

## Saga Execution State Machine

```
                    ┌─────────┐
                    │ STARTED │
                    └────┬────┘
                         │
                         ▼
                ┌────────────────────┐
                │ CONFIRMING_BOOKING │
                └────────┬───────┬───┘
                         │       │
                    success    failure
                         │       │
                         ▼       ▼
              ┌──────────────────┐  ┌────────┐
              │ RESERVING_CAPACITY│  │ FAILED │
              └────────┬───────┬─┘  └────────┘
                       │       │
                  success    failure
                       │       │
                       ▼       ▼
            ┌──────────────────────┐  ┌──────────────┐
            │ GENERATING_INVOICE   │  │ COMPENSATING │──→ FAILED
            └────────┬───────┬─────┘  └──────────────┘
                     │       │
                success    failure
                     │       │
                     ▼       ▼
        ┌───────────────────────┐  ┌──────────────┐
        │ SENDING_NOTIFICATION  │  │ COMPENSATING │──→ FAILED
        └────────┬──────────────┘  └──────────────┘
                 │
            success/failure
            (fire-and-forget)
                 │
                 ▼
           ┌───────────┐
           │ COMPLETED  │
           └───────────┘
```

**Terminal states:** `COMPLETED` and `FAILED` — no further transitions allowed.

---

## Idempotency Key Usage

Every saga execution request requires a caller-provided `Idempotency-Key` HTTP header:

```http
POST /api/v1/bookings/{bookingId}/confirm-saga
Idempotency-Key: booking-confirm-550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "voyageId": "VOY-2026-0042"
}
```

### How It Works

1. Client generates a unique idempotency key (typically `<operation>-<UUID>`)
2. Before executing, the orchestrator checks if a saga with this key already exists
3. If found: returns the existing result (no re-execution)
4. If not found: creates a new saga execution with this key
5. The key is stored with a UNIQUE constraint in the database

### Why Idempotency Matters

- **Network retries:** Clients may retry failed requests (timeout, 5xx)
- **At-least-once delivery:** Message brokers may redeliver events
- **Crash recovery:** Process may restart mid-saga execution
- **Duplicate prevention:** Ensures the booking is not confirmed twice

---

## Persistence Model

### Table: `saga_executions`

| Column | Type | Description |
|---|---|---|
| `saga_id` | UUID (PK) | Unique saga execution identifier |
| `booking_id` | VARCHAR | The booking being confirmed |
| `voyage_id` | VARCHAR | The voyage assigned to the booking |
| `status` | VARCHAR(30) | Current saga lifecycle status |
| `current_step` | VARCHAR(30) | Current or last-attempted step |
| `completed_steps` | VARCHAR(500) | Comma-separated completed step names |
| `failed_step` | VARCHAR(30) | Step that caused failure (nullable) |
| `failure_reason` | VARCHAR(1000) | Human-readable failure reason (nullable) |
| `idempotency_key` | VARCHAR(255) | Unique idempotency key |
| `started_at` | TIMESTAMPTZ | Saga start time |
| `completed_at` | TIMESTAMPTZ | Saga completion time (nullable) |
| `version` | BIGINT | Optimistic locking version |

**Indexes:** `booking_id`, `status`, `idempotency_key` (unique)

---

## Architecture Layers

The saga implementation follows Hexagonal Architecture:

```
┌─────────────────────────────────────────────────────────────────┐
│ Infrastructure Layer (Adapters)                                 │
│                                                                 │
│  Inbound:                                                       │
│    BookingController                                            │
│      └── POST /{bookingId}/confirm-saga                         │
│      └── SagaExecutionResponse (DTO)                            │
│                                                                 │
│  Outbound:                                                      │
│    JpaSagaExecutionAdapter → SpringDataSagaExecutionRepository  │
│    VesselScheduleServiceClient (with releaseCapacity)           │
│    SagaExecutionJpaEntity                                       │
├─────────────────────────────────────────────────────────────────┤
│ Application Layer                                               │
│                                                                 │
│    BookingConfirmationSaga (Orchestrator)                        │
│    SagaExecution (Domain Entity)                                │
│    SagaStatus (Enum)                                            │
│    SagaStep (Enum)                                              │
│    SagaExecutionRepository (Port)                               │
├─────────────────────────────────────────────────────────────────┤
│ Domain Layer                                                    │
│                                                                 │
│    Booking (Aggregate Root)                                     │
│    BookingService                                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## How to Add New Saga Steps

To add a new step to the Booking Confirmation Saga:

### 1. Update `SagaStep` Enum

```java
public enum SagaStep {
    CONFIRM_BOOKING(1, true, "Confirm booking"),
    RESERVE_CAPACITY(2, true, "Reserve vessel capacity"),
    GENERATE_INVOICE(3, true, "Generate invoice"),
    YOUR_NEW_STEP(4, true, "Description of new step"),  // ← Add here
    SEND_NOTIFICATION(5, false, "Send notification");    // ← Update order
    // ...
}
```

### 2. Update `SagaStatus` Enum

Add a new status for the step:

```java
public enum SagaStatus {
    // ...existing statuses...
    YOUR_NEW_STEP_STATUS,  // ← Add corresponding status
    // ...
}
```

### 3. Update `SagaExecution.mapStepToStatus()`

Map the new step to its corresponding status:

```java
private SagaStatus mapStepToStatus(SagaStep step) {
    return switch (step) {
        // ...existing mappings...
        case YOUR_NEW_STEP -> SagaStatus.YOUR_NEW_STEP_STATUS;
        // ...
    };
}
```

### 4. Add Execution Method in `BookingConfirmationSaga`

```java
private SagaExecution executeYourNewStep(SagaExecution saga, String bookingId) {
    log.info("Saga step YOUR_NEW_STEP starting: sagaId={}", saga.getSagaId());
    saga.advanceTo(SagaStep.YOUR_NEW_STEP);
    saga = sagaRepository.save(saga);

    // Call the external service
    yourServiceClient.doSomething(bookingId);

    log.info("Saga step YOUR_NEW_STEP completed: sagaId={}", saga.getSagaId());
    return saga;
}
```

### 5. Add Compensation Method

```java
private void compensateYourNewStep(SagaExecution saga, String bookingId) {
    log.info("Compensating YOUR_NEW_STEP: sagaId={}", saga.getSagaId());
    yourServiceClient.undoSomething(bookingId);
}
```

### 6. Wire Into `execute()` and `compensate()`

Add the step execution in the `execute()` method at the correct position, and add the compensation case in the `compensate()` method's switch statement.

### 7. Add Tests

Add test cases for:
- Step succeeds (happy path — update existing test)
- Step fails (compensation of all prior steps)
- Compensation of this step when a later step fails

---

## Monitoring and Observability

### Metrics

All saga methods are annotated with `@Profiled`, recording:
- `sagaStep.confirmBooking` — Step 1 duration
- `sagaStep.reserveCapacity` — Step 2 duration
- `sagaStep.generateInvoice` — Step 3 duration
- `sagaStep.sendNotification` — Step 4 duration
- `executeSaga` — Total saga duration

### Logging

| Level | When |
|---|---|
| INFO | Saga start, step start, step complete, saga complete |
| WARN | Step failure (before compensation) |
| ERROR | Compensation failure (manual intervention needed) |

### Database Queries

Find stuck sagas:
```sql
SELECT * FROM saga_executions WHERE status = 'COMPENSATING' AND completed_at IS NULL;
```

Find failed sagas for a booking:
```sql
SELECT * FROM saga_executions WHERE booking_id = ? AND status = 'FAILED' ORDER BY started_at DESC;
```

---

## References

- **ADR-004:** Saga Pattern Decision Record
- **MICROSERVICES_GUIDE.md:** Service communication patterns
- **Chris Richardson:** [Saga Pattern](https://microservices.io/patterns/data/saga.html)
- **Hector Garcia-Molina & Kenneth Salem:** "Sagas" (1987 paper)
