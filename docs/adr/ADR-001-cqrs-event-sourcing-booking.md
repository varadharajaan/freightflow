# ADR-001: Use CQRS with Event Sourcing for Booking Service

## Status

Accepted

## Date

2026-03-27

## Context

The booking service is the core domain service handling the complete booking lifecycle
(Create -> Confirm -> Amend -> Cancel). We need to decide on the architectural pattern
for managing booking state.

### Requirements
- Complete audit trail of all booking changes
- Support for complex business workflows with multiple state transitions
- Ability to replay events for debugging and analytics
- High read throughput for booking queries (dashboards, reports)
- Separate read/write optimization

### Options Considered
1. **Traditional CRUD with JPA** - Simple but lacks audit trail and event history
2. **CRUD with Audit Table** - Adds audit but loses event semantics
3. **CQRS without Event Sourcing** - Separates reads/writes but still loses event history
4. **CQRS with Event Sourcing** - Full event history, separated reads/writes, replay capability

## Decision

We will use **CQRS with Event Sourcing** for the Booking Service.

### Command Side
- Commands are handled by aggregate roots
- State changes produce domain events
- Events are persisted to an event store (PostgreSQL with JSONB)
- Kafka publishes events for downstream consumers

### Query Side
- Materialized views/projections built from events
- Optimized read models for different query patterns
- Eventually consistent with the command side

## Consequences

### Positive
- Complete audit trail via event history
- Temporal queries ("what was the booking state at time X?")
- Optimized read models for different consumers
- Events drive downstream services (tracking, billing, notifications)
- Supports event replay for debugging and reprocessing

### Negative
- Increased complexity compared to CRUD
- Eventually consistent reads (acceptable for this domain)
- Requires careful event schema versioning
- Steeper learning curve for team members

### Mitigations
- Provide comprehensive documentation and examples
- Use framework support (Axon Framework or custom lightweight implementation)
- Implement event versioning strategy from day one
- Create shared testing utilities for event-sourced aggregates

## References
- [Martin Fowler - Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Microsoft - CQRS Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs)
- [Greg Young - CQRS Documents](https://cqrs.files.wordpress.com/2010/11/cqrs_documents.pdf)
