package com.tabvault.backend.autocleanup;

import com.tabvault.backend.contentanalysis.ReminderStatus;
import com.tabvault.backend.contentanalysis.SuggestedReminder;
import com.tabvault.backend.contentanalysis.SuggestedReminderRepository;
import com.tabvault.backend.contentanalysis.UrgencyLevel;
import com.tabvault.backend.items.Item;
import com.tabvault.backend.items.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * Core service for the Auto-Cleanup Scheduler (MOD-006).
 *
 * Responsibilities:
 * <ul>
 *   <li>Create staleness reminders for non-pinned, non-archived items not visited within
 *       the user's configured threshold (AC-033)</li>
 *   <li>Auto-archive items whose staleness reminder was dismissed without a subsequent
 *       visit within the 7-day grace period (AC-034)</li>
 *   <li>Clear pending staleness reminders when the user visits an item (AC-035)</li>
 *   <li>Read/update per-user cleanup settings (AC-038, AC-039)</li>
 * </ul>
 *
 * This service is called by {@link AutoCleanupJob} which is a Quartz {@code Job} stored
 * in the JDBC job store, satisfying the production.md Shared Convention for persistence.
 */
@Service
public class AutoCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(AutoCleanupService.class);

    /**
     * Allowed staleness threshold values in days (AC-038).
     * Only these values may be stored in {@link UserCleanupSettings}.
     */
    static final Set<Integer> ALLOWED_THRESHOLD_VALUES = Set.of(14, 30, 60, 90);

    /**
     * Grace period in days between a staleness reminder dismissal and auto-archive (AC-034).
     * Fixed at 7 days per the spec.
     */
    static final int GRACE_PERIOD_DAYS = 7;

    private final UserCleanupSettingsRepository cleanupSettingsRepository;
    private final ItemRepository itemRepository;
    private final SuggestedReminderRepository reminderRepository;

    /**
     * Cron expression for the daily cleanup job — read from environment variable.
     * Defaults to 09:00 UTC daily.
     */
    private final String cleanupCron;

    public AutoCleanupService(
            UserCleanupSettingsRepository cleanupSettingsRepository,
            ItemRepository itemRepository,
            SuggestedReminderRepository reminderRepository,
            @Value("${app.autocleanup.cron:0 0 9 * * ?}") String cleanupCron) {
        this.cleanupSettingsRepository = cleanupSettingsRepository;
        this.itemRepository = itemRepository;
        this.reminderRepository = reminderRepository;
        this.cleanupCron = cleanupCron;
    }

    // -------------------------------------------------------------------------
    // AC-033, AC-034: daily cleanup execution
    // -------------------------------------------------------------------------

    /**
     * Runs the daily staleness check for all users who have not opted out.
     *
     * For each user with auto-cleanup enabled:
     * <ol>
     *   <li>Identify non-pinned, non-archived items not visited within the threshold.</li>
     *   <li>Create a PENDING staleness reminder for each stale item that does not already
     *       have a PENDING or PENDING_CONFIRMATION reminder (AC-066).</li>
     *   <li>Auto-archive items whose staleness reminder was dismissed more than
     *       {@value #GRACE_PERIOD_DAYS} days ago without a subsequent visit (AC-034).</li>
     * </ol>
     *
     * AC-039: users with auto_cleanup_enabled=false are skipped entirely.
     * The method is called by {@link AutoCleanupJob} on the Quartz schedule.
     *
     * @param userIds list of user IDs to evaluate; typically all users in the system
     */
    @Transactional
    public void runDailyCleanup(List<Long> userIds) {
        logger.info("Auto-cleanup daily job started userCount={}", userIds.size());

        int totalRemindersCreated = 0;
        int totalItemsArchived = 0;

        for (Long userId : userIds) {
            try {
                CleanupResult result = processUserCleanup(userId);
                totalRemindersCreated += result.remindersCreated();
                totalItemsArchived += result.itemsArchived();
            } catch (Exception exception) {
                // Per-user failure must not block other users
                logger.error("Auto-cleanup failed for user userId={}: {}",
                        userId, exception.getMessage(), exception);
            }
        }

        logger.info("Auto-cleanup daily job finished remindersCreated={} itemsArchived={}",
                totalRemindersCreated, totalItemsArchived);
    }

    /**
     * Processes auto-cleanup for a single user.
     *
     * @param userId the user to process
     * @return counts of reminders created and items archived for this user
     */
    @Transactional
    public CleanupResult processUserCleanup(Long userId) {
        UserCleanupSettings settings = getOrCreateSettings(userId);

        // AC-039: skip users who have opted out
        if (!settings.isAutoCleanupEnabled()) {
            logger.debug("Auto-cleanup skipped: opted out userId={}", userId);
            return new CleanupResult(0, 0);
        }

        int remindersCreated = createStalenessReminders(userId, settings.getStalenessThresholdDays());
        int itemsArchived = archiveItemsPassedGracePeriod(userId);

        logger.info("Auto-cleanup processed userId={} remindersCreated={} itemsArchived={}",
                userId, remindersCreated, itemsArchived);

        return new CleanupResult(remindersCreated, itemsArchived);
    }

    /**
     * Creates PENDING staleness reminders for non-pinned, non-archived items that have
     * not been visited within the user's configured threshold.
     *
     * AC-033: label format is "You haven't revisited this in [N] days — still need it?"
     * AC-066: skips items that already have a PENDING or PENDING_CONFIRMATION reminder.
     * AC-037: skips pinned items (enforced via the query in {@link ItemRepository}).
     *
     * @param userId             the user to check
     * @param thresholdDays      the configured staleness threshold in days
     * @return number of staleness reminders created
     */
    int createStalenessReminders(Long userId, int thresholdDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(thresholdDays);
        List<Item> staleItems = itemRepository.findStaleItemsForUser(userId, cutoff);

        int created = 0;
        for (Item item : staleItems) {
            // AC-066: idempotency — skip if PENDING reminder already exists
            boolean hasPending = !reminderRepository
                    .findByItemIdAndStatus(item.getId(), ReminderStatus.PENDING)
                    .isEmpty();

            // AC-066: also skip if PENDING_CONFIRMATION reminder exists
            boolean hasPendingConfirmation = !reminderRepository
                    .findByItemIdAndStatus(item.getId(), ReminderStatus.PENDING_CONFIRMATION)
                    .isEmpty();

            if (hasPending || hasPendingConfirmation) {
                logger.debug("Staleness reminder already exists; skipping itemId={} userId={}",
                        item.getId(), userId);
                continue;
            }

            // AC-033: create PENDING staleness reminder with the required label
            String label = "You haven't revisited this in " + thresholdDays +
                    " days — still need it?";

            SuggestedReminder reminder = new SuggestedReminder(
                    item.getId(),
                    userId,
                    LocalDate.now(ZoneOffset.UTC),
                    label,
                    UrgencyLevel.LOW
            );
            // Override status to PENDING (staleness reminder, not AI deadline confirmation)
            reminder.setStatus(ReminderStatus.PENDING);
            reminderRepository.save(reminder);

            logger.info("Staleness reminder created itemId={} userId={} thresholdDays={}",
                    item.getId(), userId, thresholdDays);
            created++;
        }
        return created;
    }

    /**
     * Auto-archives items whose staleness reminder was dismissed without a subsequent
     * visit within the grace period.
     *
     * AC-034: auto-archive 7 days after the staleness reminder is dismissed without the
     * user selecting "Keep" or visiting the item. "Keep" is modelled as the user visiting
     * the item after the reminder is shown — if {@code last_visited_at} was updated after
     * the reminder's {@code updated_at} (dismiss time), the item has been visited and is
     * not archived.
     *
     * AC-037: pinned items are never archived (enforced by the PENDING reminder check —
     *         pinned items are never given a staleness reminder, so they can never be in
     *         the dismissed-and-grace-elapsed state).
     *
     * @param userId the user whose items are evaluated for archiving
     * @return number of items archived
     */
    int archiveItemsPassedGracePeriod(Long userId) {
        OffsetDateTime graceCutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(GRACE_PERIOD_DAYS);

        // Strategy: fetch all DISMISSED reminders for this user, then for each one check
        // whether the grace period has elapsed and the item has not been visited since dismissal.
        // Items without a post-dismissal visit and with grace period elapsed are auto-archived.
        int archived = 0;

        // Fetch all non-CONFIRMED reminders for the user and filter to DISMISSED ones.
        // findByUserIdAndStatusNotOrderByDetectedDateAsc excludes only CONFIRMED, so we
        // additionally filter to DISMISSED status in memory.
        List<SuggestedReminder> dismissedReminders =
                reminderRepository.findByUserIdAndStatusNotOrderByDetectedDateAsc(userId, ReminderStatus.CONFIRMED)
                        .stream()
                        .filter(r -> r.getStatus() == ReminderStatus.DISMISSED)
                        .toList();

        for (SuggestedReminder reminder : dismissedReminders) {
            // Check grace period: was the reminder dismissed > GRACE_PERIOD_DAYS ago?
            if (reminder.getUpdatedAt() == null || !reminder.getUpdatedAt().isBefore(graceCutoff)) {
                continue;
            }

            // Fetch the item — may be null if deleted
            Item item = itemRepository.findById(reminder.getItemId()).orElse(null);
            if (item == null || item.isArchived() || item.isPinned()) {
                continue;
            }

            // AC-034: only archive if the item has NOT been visited since the reminder
            // was dismissed (last_visited_at is null or before the dismissal time).
            OffsetDateTime dismissedAt = reminder.getUpdatedAt();
            boolean visitedAfterDismissal = item.getLastVisitedAt() != null &&
                    item.getLastVisitedAt().isAfter(dismissedAt);

            if (visitedAfterDismissal) {
                logger.debug("Item visited after staleness reminder dismissed; not archiving " +
                        "itemId={} userId={}", item.getId(), userId);
                continue;
            }

            // Auto-archive the item
            item.setArchived(true);
            itemRepository.save(item);

            logger.info("Item auto-archived: staleness reminder dismissed without visit " +
                    "itemId={} userId={} reminderDismissedAt={}", item.getId(), userId, dismissedAt);
            archived++;
        }

        return archived;
    }

    // -------------------------------------------------------------------------
    // AC-035: clear staleness reminder on item visit
    // -------------------------------------------------------------------------

    /**
     * Clears any PENDING staleness reminders for the given item.
     *
     * Called by {@link com.tabvault.backend.items.ItemService} when the user records a
     * visit to the item (opens the original URL). This is the "Keep" action in the
     * staleness flow — visiting the item signals that it is still relevant.
     *
     * AC-035: the pending staleness reminder is removed and {@code last_visited_at} is
     * updated (the timestamp update is handled by the caller in {@code ItemService}).
     * AC-036: NOT called on scroll past — only called on explicit URL open.
     *
     * @param itemId the item whose staleness reminders are to be cleared
     */
    @Transactional
    public void clearStalenessRemindersOnVisit(Long itemId) {
        reminderRepository.deletePendingStalenessRemindersForItem(itemId);
        logger.debug("Staleness reminders cleared on visit itemId={}", itemId);
    }

    // -------------------------------------------------------------------------
    // AC-038, AC-039: settings CRUD
    // -------------------------------------------------------------------------

    /**
     * Returns the cleanup settings for the authenticated user.
     *
     * If no settings record exists yet, returns a synthetic response with default values.
     * The record is only persisted when the user first modifies a setting (lazy creation).
     *
     * @param userId the authenticated user's ID
     * @return the current settings (real or defaults)
     */
    @Transactional(readOnly = true)
    public CleanupSettingsResponse getSettings(Long userId) {
        return cleanupSettingsRepository.findByUserId(userId)
                .map(CleanupSettingsResponse::from)
                .orElseGet(() -> {
                    // Return defaults without persisting — avoids writing on every GET
                    UserCleanupSettings defaults = new UserCleanupSettings(userId);
                    return CleanupSettingsResponse.from(defaults);
                });
    }

    /**
     * Updates cleanup settings for the authenticated user.
     *
     * Creates the settings record if it does not exist. Only non-null fields in the
     * request are applied; null fields leave the existing value unchanged.
     *
     * AC-038: {@code stalenessThresholdDays} must be one of 14, 30, 60, or 90.
     * AC-039: {@code autoCleanupEnabled=false} disables all auto-cleanup for this user.
     *
     * @param userId  the authenticated user's ID
     * @param request the settings update request
     * @return the updated settings
     * @throws InvalidStalenessThresholdException if stalenessThresholdDays is not an allowed value
     */
    @Transactional
    public CleanupSettingsResponse updateSettings(Long userId, CleanupSettingsRequest request) {
        // Validate threshold value before touching the database
        if (request.stalenessThresholdDays() != null &&
                !ALLOWED_THRESHOLD_VALUES.contains(request.stalenessThresholdDays())) {
            throw new InvalidStalenessThresholdException(request.stalenessThresholdDays());
        }

        UserCleanupSettings settings = cleanupSettingsRepository.findByUserId(userId)
                .orElseGet(() -> new UserCleanupSettings(userId));

        if (request.stalenessThresholdDays() != null) {
            settings.setStalenessThresholdDays(request.stalenessThresholdDays());
        }
        if (request.autoCleanupEnabled() != null) {
            settings.setAutoCleanupEnabled(request.autoCleanupEnabled());
        }

        UserCleanupSettings saved = cleanupSettingsRepository.save(settings);

        logger.info("Cleanup settings updated userId={} thresholdDays={} enabled={}",
                userId, saved.getStalenessThresholdDays(), saved.isAutoCleanupEnabled());

        return CleanupSettingsResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the cleanup settings for a user, creating a default record if none exists.
     *
     * @param userId the user's ID
     * @return the existing or newly created settings
     */
    @Transactional
    public UserCleanupSettings getOrCreateSettings(Long userId) {
        return cleanupSettingsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserCleanupSettings defaults = new UserCleanupSettings(userId);
                    return cleanupSettingsRepository.save(defaults);
                });
    }

    /**
     * Returns the configured cleanup cron expression.
     * Exposed so {@link AutoCleanupQuartzConfig} can read the same value.
     *
     * @return the cron expression string
     */
    public String getCleanupCron() {
        return cleanupCron;
    }

    /**
     * Internal result record for per-user cleanup processing.
     *
     * @param remindersCreated number of staleness reminders created in this run
     * @param itemsArchived    number of items auto-archived in this run
     */
    record CleanupResult(int remindersCreated, int itemsArchived) {}
}
