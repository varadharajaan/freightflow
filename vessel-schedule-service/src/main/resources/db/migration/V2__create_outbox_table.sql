-- =============================================================================
-- V2__create_outbox_table.sql
-- FreightFlow Vessel Schedule Service — Transactional Outbox table for reliable
-- vessel event publishing.
-- =============================================================================

CREATE TABLE outbox_events (
    id              UUID            PRIMARY KEY,
    event_id        UUID            NOT NULL UNIQUE,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    UUID            NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         TEXT            NOT NULL,
    metadata        TEXT,
    status          VARCHAR(20)     NOT NULL,
    failure_count   INTEGER         NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status_created_at
    ON outbox_events (status, created_at);

CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_id, aggregate_type);
