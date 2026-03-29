-- =============================================================================
-- V5__create_saga_executions_table.sql
-- FreightFlow Booking Service — Saga execution tracking table for the
-- Booking Confirmation Saga (Saga Orchestration pattern).
--
-- Each row represents a single saga execution, tracking its progress through
-- the multi-service distributed transaction. The idempotency_key column
-- prevents duplicate saga executions for the same client request.
-- =============================================================================

CREATE TABLE saga_executions (
    -- Primary key: UUID assigned by the saga orchestrator
    saga_id             UUID            PRIMARY KEY,

    -- The booking being confirmed by this saga
    booking_id          VARCHAR(255)    NOT NULL,

    -- The voyage assigned to the booking
    voyage_id           VARCHAR(255)    NOT NULL,

    -- Current saga lifecycle status (STARTED, CONFIRMING_BOOKING, etc.)
    status              VARCHAR(30)     NOT NULL,

    -- The current or last-attempted saga step
    current_step        VARCHAR(30),

    -- Comma-separated list of completed step names (e.g., "CONFIRM_BOOKING,RESERVE_CAPACITY")
    completed_steps     VARCHAR(500),

    -- The step that caused the saga to fail (NULL if no failure)
    failed_step         VARCHAR(30),

    -- Human-readable failure reason (NULL if no failure)
    failure_reason      VARCHAR(1000),

    -- Caller-provided idempotency key — UNIQUE to prevent duplicate executions
    idempotency_key     VARCHAR(255)    NOT NULL,

    -- When the saga execution started
    started_at          TIMESTAMPTZ     NOT NULL,

    -- When the saga execution completed (NULL if still in progress)
    completed_at        TIMESTAMPTZ,

    -- Optimistic locking version for concurrent update detection
    version             BIGINT          NOT NULL DEFAULT 0,

    -- Uniqueness constraint on idempotency key
    CONSTRAINT uq_saga_idempotency_key UNIQUE (idempotency_key)
);

-- Index for looking up saga executions by booking ID
CREATE INDEX idx_saga_booking_id ON saga_executions (booking_id);

-- Index for querying sagas by status (e.g., finding stuck COMPENSATING sagas)
CREATE INDEX idx_saga_status ON saga_executions (status);

-- Index for idempotency key lookups (also covered by unique constraint, but explicit for clarity)
CREATE INDEX idx_saga_idempotency_key ON saga_executions (idempotency_key);

-- ---------------------------------------------------------------------------
-- Comments for documentation
-- ---------------------------------------------------------------------------
COMMENT ON TABLE saga_executions IS 'Tracks saga execution state for the Booking Confirmation distributed transaction (Saga Orchestration pattern).';
COMMENT ON COLUMN saga_executions.saga_id IS 'Unique saga execution identifier (UUID).';
COMMENT ON COLUMN saga_executions.booking_id IS 'The booking aggregate being confirmed by this saga.';
COMMENT ON COLUMN saga_executions.voyage_id IS 'The voyage assigned to the booking during confirmation.';
COMMENT ON COLUMN saga_executions.status IS 'Current saga lifecycle status: STARTED, CONFIRMING_BOOKING, RESERVING_CAPACITY, GENERATING_INVOICE, SENDING_NOTIFICATION, COMPLETED, COMPENSATING, FAILED.';
COMMENT ON COLUMN saga_executions.current_step IS 'The current or last-attempted step in the saga.';
COMMENT ON COLUMN saga_executions.completed_steps IS 'Comma-separated list of successfully completed step names.';
COMMENT ON COLUMN saga_executions.failed_step IS 'The step at which the saga failed (NULL if no failure).';
COMMENT ON COLUMN saga_executions.failure_reason IS 'Human-readable description of why the saga failed.';
COMMENT ON COLUMN saga_executions.idempotency_key IS 'Caller-provided key for idempotent saga execution. Must be unique.';
COMMENT ON COLUMN saga_executions.started_at IS 'Timestamp when the saga execution was initiated.';
COMMENT ON COLUMN saga_executions.completed_at IS 'Timestamp when the saga reached a terminal state (COMPLETED or FAILED).';
COMMENT ON COLUMN saga_executions.version IS 'Optimistic locking version for concurrent update detection.';
