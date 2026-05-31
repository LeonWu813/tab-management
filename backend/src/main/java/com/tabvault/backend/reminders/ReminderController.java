package com.tabvault.backend.reminders;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the Reminder Service (MOD-005).
 *
 * All endpoints require JWT authentication. The authenticated user ID is injected
 * via @AuthenticationPrincipal (set by JwtAuthenticationFilter).
 *
 * Endpoints:
 *   POST   /api/reminders               — create a manual reminder (AC-021)
 *   GET    /api/reminders               — list all active reminders for the user (AC-024)
 *   GET    /api/reminders/item/{itemId} — list reminders for a specific item (AC-024)
 *   PATCH  /api/reminders/{id}          — confirm, update, or dismiss a reminder (AC-023)
 *   POST   /api/push-subscriptions      — register a push subscription (AC-060)
 *   GET    /api/push-subscriptions/vapid-public-key — return VAPID public key (AC-061)
 */
@RestController
@RequestMapping("/api")
public class ReminderController {

    private final ReminderService reminderService;
    private final VapidConfig vapidConfig;

    public ReminderController(ReminderService reminderService, VapidConfig vapidConfig) {
        this.reminderService = reminderService;
        this.vapidConfig = vapidConfig;
    }

    // -------------------------------------------------------------------------
    // AC-021: Create a manual reminder
    // -------------------------------------------------------------------------

    /**
     * Creates a manual reminder on a saved item owned by the authenticated user.
     *
     * The item must be owned by the authenticated user. The dueDate must be a future date.
     * Label is optional; if absent, defaults to "Reminder: {item title}".
     *
     * AC-021: manual reminder with valid future due date and optional label.
     *
     * @return HTTP 201 with the created reminder record
     */
    @PostMapping("/reminders")
    public ResponseEntity<ReminderResponse> createReminder(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateReminderRequest request) {
        ReminderResponse response = reminderService.createManualReminder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // AC-024: List reminders with badge indicator
    // -------------------------------------------------------------------------

    /**
     * Returns all non-dismissed reminders for the authenticated user.
     *
     * Each reminder includes a dueWithin24Hours flag — true when the due date is today
     * or within the next 24 hours. The PWA uses this flag to display a badge indicator
     * on the item card.
     *
     * AC-024: badge indicator on item card when reminder is due within 24 hours.
     *
     * @return HTTP 200 with list of reminder response DTOs
     */
    @GetMapping("/reminders")
    public ResponseEntity<List<ReminderResponse>> listReminders(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(reminderService.listReminders(userId));
    }

    /**
     * Returns all non-dismissed reminders for a specific saved item owned by the user.
     *
     * AC-024: enables dashboard to show badge indicator per item.
     *
     * @param itemId the ID of the saved item
     * @return HTTP 200 with list of reminder response DTOs for the item
     */
    @GetMapping("/reminders/item/{itemId}")
    public ResponseEntity<List<ReminderResponse>> listRemindersForItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(reminderService.listRemindersForItem(userId, itemId));
    }

    // -------------------------------------------------------------------------
    // AC-023: Confirm, update, or dismiss a reminder
    // -------------------------------------------------------------------------

    /**
     * Updates, confirms, or dismisses an existing reminder owned by the authenticated user.
     *
     * - dismissed=true: sets status to DISMISSED
     * - dueDate provided: updates the due date (must be future)
     * - label provided: updates the label
     * - PENDING_CONFIRMATION reminders are confirmed (status → CONFIRMED) on any non-dismiss update
     *
     * AC-023: user may dismiss or update due date and label.
     *
     * @param id the reminder ID
     * @return HTTP 200 with the updated reminder record
     */
    @PatchMapping("/reminders/{id}")
    public ResponseEntity<ReminderResponse> updateReminder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateReminderRequest request) {
        ReminderResponse response = reminderService.updateReminder(userId, id, request);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // AC-060: Register a push subscription
    // -------------------------------------------------------------------------

    /**
     * Stores a Web Push subscription record for the authenticated user's device.
     *
     * Called after the client successfully calls pushManager.subscribe() and the user
     * grants browser push permission. The endpoint, auth, and p256dh values come
     * directly from the browser PushSubscription object.
     *
     * AC-060: stores endpoint URL, auth key, and p256dh key per user device.
     *
     * @return HTTP 201 with the stored subscription record
     */
    @PostMapping("/push-subscriptions")
    public ResponseEntity<PushSubscriptionResponse> registerPushSubscription(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RegisterPushSubscriptionRequest request) {
        PushSubscriptionResponse response =
                reminderService.registerPushSubscription(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the VAPID public key needed by the client to call pushManager.subscribe().
     *
     * The client uses this key as the applicationServerKey when subscribing to push.
     * This endpoint is publicly accessible (no authentication required — the client
     * needs the key before authenticating in some flows).
     *
     * AC-061: VAPID public key is read from VAPID_PUBLIC_KEY environment variable.
     */
    @GetMapping("/push-subscriptions/vapid-public-key")
    public ResponseEntity<VapidPublicKeyResponse> getVapidPublicKey() {
        return ResponseEntity.ok(new VapidPublicKeyResponse(vapidConfig.getPublicKey()));
    }
}
