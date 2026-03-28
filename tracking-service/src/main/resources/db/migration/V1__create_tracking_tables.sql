-- =============================================================================
-- V1__create_tracking_tables.sql
-- FreightFlow Tracking Service — Initial schema for container tracking.
--
-- Creates the containers, movements, and milestones tables required by the
-- Container aggregate root and its associated value objects.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Containers table — stores the Container aggregate root
-- ---------------------------------------------------------------------------
CREATE TABLE containers (
    -- Primary key: ISO container identifier (e.g., MSCU1234567)
    container_id             VARCHAR(20)     PRIMARY KEY,

    -- Booking this container is associated with
    booking_id               UUID            NOT NULL,

    -- Container lifecycle status (EMPTY, LOADED, IN_TRANSIT, AT_PORT, DELIVERED)
    status                   VARCHAR(20)     NOT NULL,

    -- Current position (nullable — no position before first reading)
    latitude                 DOUBLE PRECISION,
    longitude                DOUBLE PRECISION,
    position_timestamp       TIMESTAMPTZ,
    position_source          VARCHAR(10),

    -- Voyage assignment (populated when container departs)
    voyage_id                UUID,

    -- Audit columns
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Optimistic locking version
    version                  BIGINT          NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Movements table — stores historical position readings (time-series)
-- ---------------------------------------------------------------------------
CREATE TABLE movements (
    id                       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Reference to container
    container_id             VARCHAR(20)     NOT NULL REFERENCES containers(container_id),

    -- Position data
    latitude                 DOUBLE PRECISION NOT NULL,
    longitude                DOUBLE PRECISION NOT NULL,
    recorded_at              TIMESTAMPTZ     NOT NULL,
    source                   VARCHAR(10)     NOT NULL,

    -- Metadata
    speed_knots              DOUBLE PRECISION,
    heading_degrees          DOUBLE PRECISION,

    -- Audit
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- Milestones table — stores significant logistics milestones
-- ---------------------------------------------------------------------------
CREATE TABLE milestones (
    id                       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Reference to container
    container_id             VARCHAR(20)     NOT NULL REFERENCES containers(container_id),

    -- Milestone details
    milestone_type           VARCHAR(20)     NOT NULL,
    port                     VARCHAR(10)     NOT NULL,
    occurred_at              TIMESTAMPTZ     NOT NULL,
    description              VARCHAR(500)    NOT NULL,

    -- Audit
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- Indexes for common query patterns
-- ---------------------------------------------------------------------------

-- Container lookups by booking
CREATE INDEX idx_containers_booking_id ON containers (booking_id);

-- Container status queries
CREATE INDEX idx_containers_status ON containers (status);

-- Container voyage queries
CREATE INDEX idx_containers_voyage_id ON containers (voyage_id);

-- Movement queries by container and time
CREATE INDEX idx_movements_container_id ON movements (container_id);
CREATE INDEX idx_movements_recorded_at ON movements (recorded_at);
CREATE INDEX idx_movements_container_time ON movements (container_id, recorded_at DESC);

-- Milestone queries by container
CREATE INDEX idx_milestones_container_id ON milestones (container_id);
CREATE INDEX idx_milestones_type ON milestones (milestone_type);
CREATE INDEX idx_milestones_container_time ON milestones (container_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- Comments for documentation
-- ---------------------------------------------------------------------------
COMMENT ON TABLE containers IS 'Container aggregate root — manages container tracking lifecycle.';
COMMENT ON COLUMN containers.container_id IS 'ISO container identifier (e.g., MSCU1234567).';
COMMENT ON COLUMN containers.booking_id IS 'UUID of the booking this container is associated with.';
COMMENT ON COLUMN containers.status IS 'Current lifecycle state: EMPTY, LOADED, IN_TRANSIT, AT_PORT, or DELIVERED.';
COMMENT ON COLUMN containers.latitude IS 'Current latitude in decimal degrees (-90 to 90).';
COMMENT ON COLUMN containers.longitude IS 'Current longitude in decimal degrees (-180 to 180).';
COMMENT ON COLUMN containers.position_source IS 'Source of position data: AIS, GPS, or MANUAL.';

COMMENT ON TABLE movements IS 'Time-series of container position readings.';
COMMENT ON COLUMN movements.source IS 'Source of position data: AIS, GPS, or MANUAL.';

COMMENT ON TABLE milestones IS 'Significant logistics milestones in a container journey.';
COMMENT ON COLUMN milestones.milestone_type IS 'Milestone type: GATE_IN, LOADED, DEPARTED, ARRIVED, GATE_OUT.';
COMMENT ON COLUMN milestones.port IS 'UN/LOCODE port code where the milestone occurred.';
