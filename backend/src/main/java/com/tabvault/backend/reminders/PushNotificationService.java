package com.tabvault.backend.reminders;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Sends Web Push notifications to registered push subscription endpoints using VAPID
 * authentication (webpush-java 5.1.1).
 *
 * AC-022: dispatches push notification to all registered subscriptions for a user.
 * AC-061: reads VAPID keys from VapidConfig (injected via environment variables).
 * AC-062: deletes stale endpoint subscription when push service returns HTTP 410 Gone.
 * AC-063: iterates all active subscriptions for the user — multi-device delivery.
 */
@Service
public class PushNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    /** HTTP 410 Gone — push endpoint is no longer valid; subscription must be deleted. */
    private static final int HTTP_GONE = 410;

    private final PushService pushService;
    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    public PushNotificationService(
            VapidConfig vapidConfig,
            PushSubscriptionRepository subscriptionRepository,
            ObjectMapper objectMapper) throws Exception {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.pushService = new PushService(
                vapidConfig.getPublicKey(),
                vapidConfig.getPrivateKey(),
                vapidConfig.getSubject()
        );
    }

    /**
     * Sends a push notification to all registered subscriptions for the given user.
     *
     * The notification payload contains the item title and reminder label.
     * If the push service returns HTTP 410 Gone for an endpoint, that subscription
     * record is deleted and delivery is not retried for that endpoint.
     *
     * AC-022: delivers notification containing item title and reminder label.
     * AC-062: deletes subscription on HTTP 410 Gone response.
     * AC-063: iterates all subscriptions — one push per registered device.
     *
     * @param userId      the user whose subscriptions receive the notification
     * @param itemTitle   the title of the saved item
     * @param reminderLabel the label describing the reminder
     */
    public void sendReminderNotification(Long userId, String itemTitle, String reminderLabel) {
        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            logger.info("No push subscriptions for user; skipping notification userId={}", userId);
            return;
        }

        String payload = buildPayload(itemTitle, reminderLabel);

        for (PushSubscription subscription : subscriptions) {
            deliverToSubscription(subscription, payload);
        }
    }

    /**
     * Builds the JSON notification payload with title and body fields.
     */
    private String buildPayload(String itemTitle, String reminderLabel) {
        try {
            Map<String, String> payloadMap = Map.of(
                    "title", itemTitle != null ? itemTitle : "TabVault Reminder",
                    "body", reminderLabel != null ? reminderLabel : "You have a reminder due today"
            );
            return objectMapper.writeValueAsString(payloadMap);
        } catch (Exception exception) {
            logger.warn("Failed to serialize push notification payload; using fallback payload",
                    exception);
            return "{\"title\":\"TabVault Reminder\",\"body\":\"You have a reminder due today\"}";
        }
    }

    /**
     * Attempts to deliver a push notification to a single subscription endpoint.
     *
     * On HTTP 410 Gone, deletes the stale subscription record (AC-062).
     * On other errors, logs a warning and continues — non-fatal per-endpoint failure
     * must not block delivery to remaining subscriptions.
     */
    private void deliverToSubscription(PushSubscription subscription, String payload) {
        try {
            Subscription webPushSubscription = new Subscription(
                    subscription.getEndpoint(),
                    new Subscription.Keys(subscription.getP256dhKey(), subscription.getAuthKey())
            );

            Notification notification = new Notification(webPushSubscription, payload);
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HTTP_GONE) {
                // AC-062: push service signals this endpoint is permanently invalid.
                // Delete the subscription record — no further delivery attempts.
                logger.info("Push endpoint returned 410 Gone; deleting stale subscription " +
                        "subscriptionId={} userId={} endpoint={}",
                        subscription.getId(), subscription.getUserId(),
                        abbreviateEndpoint(subscription.getEndpoint()));
                subscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
            } else if (statusCode >= 400) {
                logger.warn("Push delivery failed with non-410 error " +
                        "subscriptionId={} userId={} statusCode={}",
                        subscription.getId(), subscription.getUserId(), statusCode);
            } else {
                logger.info("Push notification delivered subscriptionId={} userId={} statusCode={}",
                        subscription.getId(), subscription.getUserId(), statusCode);
            }
        } catch (Exception exception) {
            logger.warn("Failed to deliver push notification to subscription " +
                    "subscriptionId={} userId={}: {}",
                    subscription.getId(), subscription.getUserId(), exception.getMessage());
        }
    }

    /**
     * Truncates an endpoint URL for safe log output (avoids extremely long log lines).
     */
    private String abbreviateEndpoint(String endpoint) {
        if (endpoint == null) {
            return "(null)";
        }
        return endpoint.length() > 60 ? endpoint.substring(0, 60) + "..." : endpoint;
    }
}
