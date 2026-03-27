# ADR-004: Saga Pattern (Orchestration) for Distributed Transactions

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow's database-per-service architecture (ADR-002) means that business operations
spanning multiple services — such as creating a booking that involves reserving capacity,
initiating tracking, generating a billing record, and sending notifications — cannot rely
on a single ACID transaction. We need a strategy for maintaining data consistency across
service boundaries without distributed two-phase commit (2PC).

### Requirements
- Data consistency across Booking, Tracking, Billing, and Notification services for
  multi-step business workflows
- Automatic rollback (compensation) when any step in a multi-service workflow fails
- Visibility into the current state of long-running business processes
- Timeout handling for steps that take too long or where downstream services are unavailable
- Auditability — full history of saga execution for compliance and debugging
- Support for both synchronous (API) and asynchronous (Kafka) step execution

### Options Considered
1. **Two-Phase Commit (2PC)** - Traditional distributed transaction protocol. Provides strong
   consistency but requires all participants to support XA transactions. Creates tight temporal
   coupling (all participants must be available simultaneously). Known for poor performance
   and availability characteristics. Not viable with database-per-service and heterogeneous
   data stores.
2. **Saga — Choreography** - Each service publishes events and reacts to events from other
   services. No central coordinator. Simple for 2-3 step workflows. However, as workflow
   complexity grows, the interaction logic is scattered across services, making it extremely
   difficult to reason about the overall process, detect failures, and implement timeouts.
   Cyclic event dependencies are a real risk. Debugging requires correlating events across
   multiple service logs.
3. **Saga — Orchestration** - A central orchestrator (saga coordinator) defines the workflow
   sequence, invokes each participant, and handles compensations on failure. The workflow
   logic is centralized and explicit. Easy to add/modify steps. Clear visibility into saga
   state. Slightly more coupling to the orchestrator, but the orchestrator only knows about
   service interfaces, not implementations.
4. **Routing Slip Pattern** - The message itself carries the list of remaining steps. Each
   service processes and forwards. Lightweight but lacks centralized state management and
   compensation logic becomes complex.

## Decision

We will use the **Saga Orchestration** pattern for all distributed transactions in FreightFlow.
The orchestrator will be implemented using **Spring Statemachine** to model saga state
transitions explicitly.

### Why Orchestration over Choreography
For FreightFlow's booking workflow, a single booking creation involves 4-5 services in a
specific sequence with conditional branching (e.g., hazardous materials require additional
compliance checks). Choreography would scatter this logic across services, making it:
- **Hard to visualize** — no single place shows the complete workflow
- **Hard to modify** — adding a step requires changes in multiple services
- **Hard to monitor** — no central saga state to query for stuck/failed workflows
- **Hard to compensate** — compensating transactions must be triggered by absence of events
  (timeout-based), which is fragile

Orchestration provides a single source of truth for each workflow's definition and state.

### Saga Orchestrator Design

#### Booking Creation Saga
```
[Start] → Reserve Capacity → Initiate Tracking → Create Billing Record → Send Notification → [Complete]
              ↓ fail              ↓ fail               ↓ fail
         [Compensate]       Release Capacity      Release Capacity +
                                                  Cancel Tracking
```

#### Spring Statemachine Implementation
- Each saga type (BookingCreation, BookingCancellation, BookingAmendment) is modeled as a
  `StateMachineConfig` with explicit states, transitions, guards, and actions.
- States: `STARTED`, `CAPACITY_RESERVED`, `TRACKING_INITIATED`, `BILLING_CREATED`,
  `NOTIFICATION_SENT`, `COMPLETED`, `COMPENSATING`, `FAILED`
- Events: `RESERVE_CAPACITY`, `CAPACITY_RESERVED_OK`, `CAPACITY_RESERVED_FAIL`,
  `INITIATE_TRACKING`, etc.
- Guards validate preconditions before state transitions.
- Actions execute the actual service calls (via Kafka commands or REST with Resilience4j).

#### Saga Persistence
- Saga state is persisted to PostgreSQL via Spring Statemachine's `StateMachinePersister`.
- Each saga instance has a unique `sagaId` (UUID) correlated with the `bookingId`.
- The `saga_instances` table stores: `saga_id`, `saga_type`, `current_state`, `payload`
  (JSONB), `created_at`, `updated_at`, `deadline_at`.
- Historical state transitions are logged to a `saga_events` table for audit.

### Compensating Transactions
Each saga step has a corresponding compensating transaction that undoes its effect:

| Step                  | Forward Action              | Compensating Action              |
|-----------------------|-----------------------------|----------------------------------|
| Reserve Capacity      | `POST /capacity/reserve`    | `POST /capacity/release`         |
| Initiate Tracking     | `POST /tracking/initiate`   | `POST /tracking/cancel`          |
| Create Billing Record | `POST /billing/create`      | `POST /billing/void`             |
| Send Notification     | `POST /notification/send`   | (No compensation — idempotent)   |

- Compensations are executed in **reverse order** of the completed forward steps.
- Compensating actions are designed to be **idempotent** — executing them multiple times
  produces the same result (critical for retry scenarios).
- If a compensation itself fails, the saga enters a `FAILED` state and raises an alert
  for manual intervention via a dead letter mechanism.

### Timeout and Deadline Handling
- Each saga step has a configurable timeout (e.g., 30 seconds for capacity reservation).
- A scheduled job polls for sagas past their `deadline_at` and triggers compensation.
- Timeouts are managed via Spring's `@Scheduled` tasks checking the `saga_instances` table.

### Idempotency
- Every saga command carries an `idempotencyKey` (derived from `sagaId` + `stepName`).
- Participant services use this key to detect and deduplicate retried commands.
- This ensures that network retries or Kafka redeliveries do not cause duplicate side effects.

## Consequences

### Positive
- Centralized workflow logic makes complex multi-service processes easy to understand,
  modify, and debug
- Explicit state machine provides clear visibility into saga progress — dashboards can
  show stuck/failed sagas for operational monitoring
- Compensating transactions provide a well-defined rollback strategy without 2PC
- Saga audit log (state transitions) satisfies compliance requirements for booking workflows
- Spring Statemachine provides a mature, battle-tested state machine framework with
  persistence support
- Timeout handling prevents sagas from being stuck indefinitely

### Negative
- The orchestrator is a potential single point of failure (if the orchestrator service is
  down, no sagas can progress)
- Adds complexity — saga definition, compensation logic, and timeout handling require
  careful design
- Orchestrator has semantic coupling to participant service interfaces (must know what
  commands to send)
- Spring Statemachine has a learning curve and can be verbose for complex state machines
- Compensation semantics are not always straightforward — some actions are inherently
  non-compensable (e.g., sending an email)

### Mitigations
- Deploy the saga orchestrator with multiple replicas behind Kubernetes for high availability;
  saga state in PostgreSQL ensures any instance can resume a saga
- Implement comprehensive monitoring: track saga duration percentiles, failure rates, and
  compensation frequencies via Micrometer metrics
- For non-compensable steps (e.g., notifications), place them at the end of the saga so
  they execute only after all compensable steps have succeeded
- Create a `freightflow-saga-starter` library with base classes, common patterns, and
  testing utilities to reduce boilerplate and ensure consistency
- Build an operational dashboard showing active sagas, their states, and stuck/failed
  instances with manual retry/skip capabilities

## References
- [Chris Richardson - Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Caitie McCaffrey - Applying the Saga Pattern (Talk)](https://www.youtube.com/watch?v=xDuwrtwYHu8)
- [Spring Statemachine Reference](https://docs.spring.io/spring-statemachine/docs/current/reference/)
- [Hector Garcia-Molina - Sagas (Original 1987 Paper)](https://www.cs.cornell.edu/andru/cs711/2002fa/reading/sagas.pdf)
- [Microsoft - Saga Pattern](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
