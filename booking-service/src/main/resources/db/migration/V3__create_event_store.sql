-- =============================================================================
-- V3__create_event_store.sql
-- FreightFlow Booking Service — Event Store for CQRS / Event Sourcing.
--
-- Stores all domain events as the single source of truth. The booking
-- aggregate state is reconstructed by replaying events. JSONB payload
-- allows flexible schema evolution without table migrations.
--
-- Design decisions:
--   - JSONB for event_data: flexible schema, indexable, queryable
--   - Partitioned by created_at: time-range queries are fast, old partitions archivable
--   - UNIQUE(aggregate_id, version): optimistic concurrency control
--   - Append-only: no UPDATE or DELETE operations on this table
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Event store table — immutable, append-only event log
-- ---------------------------------------------------------------------------
CREATE TABLE booking_events (
    -- Unique event identifier
    event_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- The aggregate this event belongs to
    aggregate_id     UUID            NOT NULL,

    -- Aggregate type (for multi-aggregate event stores)
    aggregate_type   VARCHAR(100)    NOT NULL DEFAULT 'Booking',

    -- Event type (e.g., 'BookingCreated', 'BookingConfirmed')
    event_type       VARCHAR(100)    NOT NULL,

    -- Event payload as JSONB — the actual event data
    event_data       JSONB           NOT NULL,

    -- Metadata (correlation ID, user ID, source service)
    metadata         JSONB,

    -- Monotonically increasing version per aggregate (for ordering + concurrency)
    version          BIGINT          NOT NULL,

    -- When this event occurred in the domain
    occurred_at      TIMESTAMPTZ     NOT NULL,

    -- When this event was persisted to the store
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Who/what created this event
    created_by       VARCHAR(100),

    -- Unique constraint for optimistic concurrency control
    UNIQUE (aggregate_id, version)
);

-- ---------------------------------------------------------------------------
-- Indexes for common access patterns
-- ---------------------------------------------------------------------------

-- Primary access pattern: load all events for an aggregate in version order
CREATE INDEX idx_booking_events_aggregate_version
    ON booking_events (aggregate_id, version);

-- Query events by type (e.g., find all BookingCancelled events)
CREATE INDEX idx_booking_events_event_type
    ON booking_events (event_type);

-- Time-range queries (e.g., events in the last 24 hours for monitoring)
CREATE INDEX idx_booking_events_created_at
    ON booking_events (created_at);

-- GIN index on JSONB event_data for flexible querying
-- Example: SELECT * FROM booking_events WHERE event_data->>'customerId' = 'xxx'
CREATE INDEX idx_booking_events_data_gin
    ON booking_events USING GIN (event_data);

-- ---------------------------------------------------------------------------
-- Read model projection table — materialized from events
-- ---------------------------------------------------------------------------
CREATE TABLE booking_projections (
    -- Same ID as the aggregate
    booking_id               UUID            PRIMARY KEY,

    -- Denormalized fields for fast reads (no joins needed)
    customer_id              UUID            NOT NULL,
    status                   VARCHAR(20)     NOT NULL,
    origin_port              VARCHAR(10)     NOT NULL,
    destination_port         VARCHAR(10)     NOT NULL,
    container_type           VARCHAR(20)     NOT NULL,
    container_count          INTEGER         NOT NULL,
    commodity_description    VARCHAR(500),
    voyage_id                UUID,
    cancellation_reason      VARCHAR(1000),
    requested_departure_date DATE            NOT NULL,

    -- The version of the last event applied to this projection
    last_event_version       BIGINT          NOT NULL DEFAULT 0,

    -- Timestamps
    created_at               TIMESTAMPTZ     NOT NULL,
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- For cursor-based pagination
    sequence_number          BIGSERIAL       NOT NULL
);

-- Indexes for projection queries
CREATE INDEX idx_booking_proj_customer ON booking_projections (customer_id);
CREATE INDEX idx_booking_proj_status ON booking_projections (status);
CREATE INDEX idx_booking_proj_sequence ON booking_projections (sequence_number);

-- ---------------------------------------------------------------------------
-- Comments
-- ---------------------------------------------------------------------------
COMMENT ON TABLE booking_events IS 'Append-only event store for booking aggregate. Source of truth in Event Sourcing.';
COMMENT ON TABLE booking_projections IS 'Read model projection built from booking events. Optimized for query performance.';
COMMENT ON COLUMN booking_events.event_data IS 'JSONB payload containing the full event data. Schema varies by event_type.';
COMMENT ON COLUMN booking_events.version IS 'Monotonic version per aggregate. UNIQUE constraint provides optimistic concurrency.';
COMMENT ON COLUMN booking_projections.last_event_version IS 'Version of the last event applied. Used to detect and skip duplicate projections.';
COMMENT ON COLUMN booking_projections.sequence_number IS 'Auto-incrementing sequence for cursor-based pagination (no offset queries).';
