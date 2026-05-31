-- V12: Create user_cleanup_settings table
-- Stores per-user auto-cleanup preferences.
--
-- staleness_threshold_days: number of days without a visit after which an item is considered
--   stale and a staleness reminder is created. Allowed values: 14, 30, 60, 90. Default: 30.
-- auto_cleanup_enabled: when FALSE, neither staleness reminders nor auto-archiving run for
--   the user. Corresponds to the opt-out toggle in account settings.
-- grace_period_days: days after a staleness reminder is dismissed (without "Keep" or visit)
--   before the item is auto-archived. Fixed at 7 days per spec.

CREATE TABLE user_cleanup_settings (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    staleness_threshold_days  INT NOT NULL DEFAULT 30,
    auto_cleanup_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_cleanup_settings_user UNIQUE (user_id),
    CONSTRAINT ck_staleness_threshold CHECK (staleness_threshold_days IN (14, 30, 60, 90))
);

CREATE INDEX idx_user_cleanup_settings_user_id ON user_cleanup_settings (user_id);
