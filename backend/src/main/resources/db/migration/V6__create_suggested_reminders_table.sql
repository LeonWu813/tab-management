-- V6: Create suggested_reminders table
-- Stores AI-detected deadline reminders created by the content analysis pipeline (MOD-003).
-- Each reminder starts in 'pending_confirmation' status and must be explicitly confirmed
-- by the user before it can trigger push notifications (AC-012, AC-013).

CREATE TYPE reminder_status AS ENUM ('pending_confirmation', 'confirmed', 'dismissed');
CREATE TYPE urgency_level AS ENUM ('low', 'medium', 'high');

CREATE TABLE suggested_reminders (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    detected_date   DATE NOT NULL,
    label           VARCHAR(500) NOT NULL,
    urgency         urgency_level NOT NULL DEFAULT 'medium',
    status          reminder_status NOT NULL DEFAULT 'pending_confirmation',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_suggested_reminders_item_id ON suggested_reminders (item_id);
CREATE INDEX idx_suggested_reminders_user_id ON suggested_reminders (user_id);
CREATE INDEX idx_suggested_reminders_status  ON suggested_reminders (status);
