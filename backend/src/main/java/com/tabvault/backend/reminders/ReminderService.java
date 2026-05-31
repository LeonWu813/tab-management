package com.tabvault.backend.reminders;

import com.tabvault.backend.contentanalysis.ReminderStatus;
import com.tabvault.backend.contentanalysis.SuggestedReminder;
import com.tabvault.backend.contentanalysis.SuggestedReminderRepository;
import com.tabvault.backend.contentanalysis.UrgencyLevel;
import com.tabvault.backend.items.Item;
import com.tabvault.backend.items.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the Reminder Service (MOD-005).
 *
 * Handles:
 * - Manual reminder creation on user-owned items (AC-021)
 * - Confirming AI-suggested reminders (AC-023)
 * - Dismissing reminders (AC-023)
 * - Updating reminder due date and label (AC-023)
 * - Listing active reminders for a user with badge indicator (AC-024)
 * - Storing push subscription records (AC-060)
 */
@Service
public class ReminderService {

    private static final Logger logger = LoggerFactory.getLogger(ReminderService.class);

    private final SuggestedReminderRepository reminderRepository;
    private final ItemRepository itemRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;

    public ReminderService(
            SuggestedReminderRepository reminderRepository,
            ItemRepository itemRepository,
            PushSubscriptionRepository pushSubscriptionRepository) {
        this.reminderRepository = reminderRepository;
        this.itemRepository = itemRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    // -------------------------------------------------------------------------
    // AC-021: Create a manual reminder on a user-owned item
    // -------------------------------------------------------------------------

    /**
     * Creates a manual reminder for a saved item owned by the user.
     *
     * The reminder is created with CONFIRMED status immediately because the user
     * explicitly requested it — no pending-confirmation step needed for manual reminders.
     *
     * AC-021: valid future due date required; item must be owned by the user.
     *
     * @param userId  the authenticated user creating the reminder
     * @param request the create request with itemId, dueDate, and optional label
     * @return the created reminder record as a response DTO
     * @throws ReminderItemNotFoundException if the item does not exist or is not owned by the user
     */
    @Transactional
    public ReminderResponse createManualReminder(Long userId, CreateReminderRequest request) {
        Item item = itemRepository.findByIdAndUserId(request.itemId(), userId)
                .orElseThrow(() -> new ReminderItemNotFoundException(request.itemId()));

        String label = resolveLabel(request.label(), item.getTitle());

        SuggestedReminder reminder = new SuggestedReminder(
                item.getId(),
                userId,
                request.dueDate(),
                label,
                UrgencyLevel.MEDIUM
        );
        // Manual reminders are confirmed immediately — no pending step.
        reminder.setStatus(ReminderStatus.CONFIRMED);

        SuggestedReminder saved = reminderRepository.save(reminder);
        logger.info("Manual reminder created reminderId={} itemId={} userId={} dueDate={}",
                saved.getId(), item.getId(), userId, request.dueDate());

        return ReminderResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // AC-023: Confirm, update, or dismiss a reminder
    // -------------------------------------------------------------------------

    /**
     * Updates, confirms, or dismisses an existing reminder.
     *
     * Rules:
     * - If dismissed=true, the reminder status is set to DISMISSED regardless of other fields.
     * - Otherwise, if dueDate is provided, it replaces the current detectedDate.
     * - If label is provided, it replaces the current label.
     * - If dueDate is absent (null), the existing date is preserved.
     * - If none of dismissed/dueDate/label is provided, the reminder is confirmed (PENDING → CONFIRMED).
     *
     * AC-023: the reminder must be owned by the authenticated user.
     *
     * @param userId     the authenticated user performing the update
     * @param reminderId the ID of the reminder to update
     * @param request    the update request
     * @return the updated reminder record as a response DTO
     * @throws ReminderNotFoundException if the reminder does not exist or is not owned by the user
     */
    @Transactional
    public ReminderResponse updateReminder(Long userId, Long reminderId, UpdateReminderRequest request) {
        SuggestedReminder reminder = reminderRepository.findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new ReminderNotFoundException(reminderId));

        if (Boolean.TRUE.equals(request.dismissed())) {
            // AC-023: dismiss the reminder
            reminder.setStatus(ReminderStatus.DISMISSED);
            logger.info("Reminder dismissed reminderId={} userId={}", reminderId, userId);
        } else {
            // AC-023: update date and/or label, and confirm if it was pending
            if (request.dueDate() != null) {
                reminder.setDetectedDate(request.dueDate());
            }
            if (request.label() != null && !request.label().isBlank()) {
                reminder.setLabel(request.label());
            }
            // Confirm the reminder if it was pending (PENDING_CONFIRMATION → CONFIRMED)
            if (reminder.getStatus() == ReminderStatus.PENDING_CONFIRMATION) {
                reminder.setStatus(ReminderStatus.CONFIRMED);
                logger.info("Reminder confirmed reminderId={} userId={}", reminderId, userId);
            } else {
                logger.info("Reminder updated reminderId={} userId={} newDueDate={}",
                        reminderId, userId, reminder.getDetectedDate());
            }
        }

        SuggestedReminder saved = reminderRepository.save(reminder);
        return ReminderResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // Listing reminders (with badge indicator for AC-024)
    // -------------------------------------------------------------------------

    /**
     * Returns all non-dismissed reminders for the authenticated user, ordered by due date.
     *
     * Each ReminderResponse includes a dueWithin24Hours flag computed from the due date.
     * This flag drives the badge indicator on the dashboard item card (AC-024).
     *
     * AC-024: badge indicator when the item has a reminder due within the next 24 hours.
     *
     * @param userId the authenticated user
     * @return list of active reminder response DTOs with dueWithin24Hours badge indicator
     */
    @Transactional(readOnly = true)
    public List<ReminderResponse> listReminders(Long userId) {
        return reminderRepository
                .findByUserIdAndStatusNotOrderByDetectedDateAsc(userId, ReminderStatus.DISMISSED)
                .stream()
                .map(ReminderResponse::from)
                .toList();
    }

    /**
     * Returns all reminders for a specific item owned by the user.
     *
     * Used to determine whether to show a badge on a specific item card (AC-024).
     *
     * @param userId the authenticated user
     * @param itemId the item whose reminders are returned
     * @return list of reminder response DTOs for the item
     */
    @Transactional(readOnly = true)
    public List<ReminderResponse> listRemindersForItem(Long userId, Long itemId) {
        // Verify ownership first
        itemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ReminderItemNotFoundException(itemId));

        return reminderRepository.findByItemId(itemId)
                .stream()
                .filter(r -> r.getStatus() != ReminderStatus.DISMISSED)
                .map(ReminderResponse::from)
                .toList();
    }

    // -------------------------------------------------------------------------
    // AC-060: Register a push subscription
    // -------------------------------------------------------------------------

    /**
     * Stores a push subscription record for the authenticated user's device.
     *
     * If a subscription with the same endpoint URL already exists (e.g., re-registration
     * after browser refresh), the existing record is updated in place to keep the keys
     * current. This prevents duplicate endpoint rows.
     *
     * AC-060: stores endpoint URL, auth key, and p256dh key per user device.
     *
     * @param userId  the authenticated user granting push permission
     * @param request the push subscription data from the browser
     * @return the stored subscription record as a response DTO
     */
    @Transactional
    public PushSubscriptionResponse registerPushSubscription(
            Long userId, RegisterPushSubscriptionRequest request) {

        // Upsert: if the endpoint already exists (same device re-registers), update keys.
        PushSubscription subscription = pushSubscriptionRepository
                .findByEndpoint(request.endpoint())
                .map(existing -> {
                    // Endpoint belongs to a different user — this should not normally happen,
                    // but treat it as a new registration for the current user.
                    logger.info("Push subscription endpoint already registered; updating record " +
                            "endpoint={} userId={}", abbreviateEndpoint(request.endpoint()), userId);
                    return existing;
                })
                .orElse(null);

        if (subscription == null) {
            subscription = new PushSubscription(
                    userId, request.endpoint(), request.auth(), request.p256dh());
        }

        PushSubscription saved = pushSubscriptionRepository.save(subscription);
        logger.info("Push subscription registered subscriptionId={} userId={} endpoint={}",
                saved.getId(), userId, abbreviateEndpoint(request.endpoint()));

        return PushSubscriptionResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveLabel(String requestedLabel, String itemTitle) {
        if (requestedLabel != null && !requestedLabel.isBlank()) {
            return requestedLabel;
        }
        return itemTitle != null ? "Reminder: " + itemTitle : "Reminder";
    }

    private String abbreviateEndpoint(String endpoint) {
        if (endpoint == null) {
            return "(null)";
        }
        return endpoint.length() > 60 ? endpoint.substring(0, 60) + "..." : endpoint;
    }
}
