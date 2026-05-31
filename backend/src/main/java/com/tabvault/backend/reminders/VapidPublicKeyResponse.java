package com.tabvault.backend.reminders;

/**
 * Response DTO containing the VAPID public key.
 *
 * Returned by GET /api/push-subscriptions/vapid-public-key.
 * The client uses this key as the applicationServerKey parameter when calling
 * pushManager.subscribe() to obtain a push subscription.
 *
 * AC-061: VAPID public key read from VAPID_PUBLIC_KEY environment variable.
 */
public record VapidPublicKeyResponse(String publicKey) {
}
