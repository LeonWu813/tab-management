-- V10: Create push_subscriptions table
-- Stores Web Push API subscription records per user per device.
-- Each subscription contains the push service endpoint URL, auth key, and p256dh key
-- obtained from the browser's PushSubscription object after the user grants push permission.
--
-- A user may have multiple active subscriptions (one per registered device/browser).
-- Subscriptions are removed when the Web Push service returns HTTP 410 Gone (AC-062).
--
-- AC-060: stores endpoint, auth, p256dh per user device
-- AC-063: multiple subscriptions per user for multi-device delivery

CREATE TABLE push_subscriptions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint    TEXT NOT NULL,
    auth_key    TEXT NOT NULL,
    p256dh_key  TEXT NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions (user_id);
