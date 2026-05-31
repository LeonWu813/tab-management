package com.tabvault.backend.reminders;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for registering a Web Push subscription for the authenticated user's device.
 *
 * These values come directly from the browser PushSubscription object after the user
 * grants push notification permission.
 *
 * AC-060: stores endpoint URL, auth key, and p256dh key per user device.
 */
public record RegisterPushSubscriptionRequest(

        /**
         * The push service endpoint URL provided by the browser.
         * Unique per browser/device.
         */
        @NotBlank(message = "Endpoint URL is required")
        String endpoint,

        /**
         * The auth key (URL-safe base64) from the browser PushSubscription keys object.
         * Required for encrypted push message delivery.
         */
        @NotBlank(message = "Auth key is required")
        String auth,

        /**
         * The p256dh key (URL-safe base64) from the browser PushSubscription keys object.
         * Required for encrypted push message delivery (ECDH key agreement).
         */
        @NotBlank(message = "p256dh key is required")
        String p256dh
) {
}
