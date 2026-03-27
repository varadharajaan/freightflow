-- =============================================================================
-- V2__add_audit_by_columns.sql
-- FreightFlow Booking Service — Add created_by and updated_by audit columns.
--
-- Supports JPA auditing via @CreatedBy and @LastModifiedBy annotations.
-- Uses expand-contract pattern: new columns are nullable (backward compatible).
-- =============================================================================

ALTER TABLE bookings ADD COLUMN created_by VARCHAR(100);
ALTER TABLE bookings ADD COLUMN updated_by VARCHAR(100);

COMMENT ON COLUMN bookings.created_by IS 'User or system that created the booking (populated by JPA auditing).';
COMMENT ON COLUMN bookings.updated_by IS 'User or system that last modified the booking (populated by JPA auditing).';
