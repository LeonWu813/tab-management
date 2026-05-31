-- V8: Fix enum casing for reminder_status and urgency_level
-- V6 created these enums with lowercase values ('pending_confirmation', 'low', etc.)
-- but Java enums ReminderStatus and UrgencyLevel use uppercase .name() values
-- ('PENDING_CONFIRMATION', 'LOW', etc.). @JdbcTypeCode(SqlTypes.NAMED_ENUM) passes
-- .name() directly to PostgreSQL, which rejects the uppercase values because
-- PostgreSQL enum comparisons are case-sensitive.
--
-- Fix: drop suggested_reminders (no prod data to preserve), drop the mismatched enum
-- types, recreate both enums with uppercase values matching Java convention, then
-- recreate the table. The job_status enum in V5 already uses uppercase values
-- (PENDING, PROCESSING, COMPLETED, FAILED) — this migration aligns V6 types to the
-- same convention.
--
-- AC-012, AC-013

-- 1. Drop dependent table first (FK references prevent enum drop otherwise)
DROP TABLE IF EXISTS suggested_reminders;

-- 2. Drop the old lowercase enum types
DROP TYPE IF EXISTS reminder_status;
DROP TYPE IF EXISTS urgency_level;

-- 3. Recreate with uppercase values matching Java enum .name() output
CREATE TYPE reminder_status AS ENUM ('PENDING_CONFIRMATION', 'CONFIRMED', 'DISMISSED');
CREATE TYPE urgency_level   AS ENUM ('LOW', 'MEDIUM', 'HIGH');

-- 4. Recreate suggested_reminders with the corrected enum column types
CREATE TABLE suggested_reminders (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    detected_date   DATE NOT NULL,
    label           VARCHAR(500) NOT NULL,
    urgency         urgency_level   NOT NULL DEFAULT 'MEDIUM',
    status          reminder_status NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_suggested_reminders_item_id ON suggested_reminders (item_id);
CREATE INDEX idx_suggested_reminders_user_id ON suggested_reminders (user_id);
CREATE INDEX idx_suggested_reminders_status  ON suggested_reminders (status);
