-- =============================================================================
-- schema-h2.sql
-- H2 test database schema for booking-service tests.
-- Creates tables that are normally created by Flyway migrations but need to
-- exist when using H2 in-memory database for integration tests.
-- =============================================================================

-- Outbox events table for the Transactional Outbox pattern
CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID            DEFAULT RANDOM_UUID() PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    UUID            NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         CLOB            NOT NULL,
    metadata        CLOB,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed       BOOLEAN         DEFAULT FALSE NOT NULL,
    processed_at    TIMESTAMP WITH TIME ZONE
);

-- Index for the polling processor: efficiently find unprocessed events in order
CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON outbox_events (created_at);

-- Index for aggregate-based lookups
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_events (aggregate_id, aggregate_type);
