package com.tabvault.backend.reminders;

import com.tabvault.backend.contentanalysis.SuggestedReminder;
import com.tabvault.backend.contentanalysis.SuggestedReminderRepository;
import com.tabvault.backend.items.Item;
import com.tabvault.backend.items.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled job that evaluates CONFIRMED reminders daily and dispatches push
 * notifications when a reminder's due date matches today's date.
 *
 * Runs once per day at 08:00 UTC by default (configurable via
 * {@code app.reminders.dispatch-cron} environment variable).
 *
 * AC-022: dispatches push notifications to all registered subscriptions for the user
 *         when a reminder's due time is reached, containing the item title and label.
 */
@Component
public class ReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReminderScheduler.class);

    private final SuggestedReminderRepository reminderRepository;
    private final ItemRepository itemRepository;
    private final PushNotificationService pushNotificationService;

    public ReminderScheduler(
            SuggestedReminderRepository reminderRepository,
            ItemRepository itemRepository,
            PushNotificationService pushNotificationService) {
        this.reminderRepository = reminderRepository;
        this.itemRepository = itemRepository;
        this.pushNotificationService = pushNotificationService;
    }

    /**
     * Evaluates CONFIRMED reminders due today and dispatches push notifications.
     *
     * Cron expression is read from {@code app.reminders.dispatch-cron} which is set from
     * the {@code REMINDER_DISPATCH_CRON} environment variable.
     * Default: "0 0 8 * * *" — runs at 08:00 UTC every day.
     *
     * AC-022: dispatches to all registered push subscriptions for the user when due.
     */
    @Scheduled(cron = "${app.reminders.dispatch-cron:0 0 8 * * *}")
    public void dispatchDueReminders() {
        LocalDate today = LocalDate.now();
        logger.info("Reminder dispatch job started date={}", today);

        List<SuggestedReminder> dueReminders = reminderRepository.findConfirmedRemindersDueOn(today);

        if (dueReminders.isEmpty()) {
            logger.info("No reminders due today; dispatch job finished date={}", today);
            return;
        }

        logger.info("Dispatching notifications for due reminders count={} date={}",
                dueReminders.size(), today);

        int dispatched = 0;
        int failed = 0;

        for (SuggestedReminder reminder : dueReminders) {
            try {
                String itemTitle = resolveItemTitle(reminder.getItemId());
                String label = reminder.getLabel();

                pushNotificationService.sendReminderNotification(
                        reminder.getUserId(), itemTitle, label);
                dispatched++;

                logger.info("Push notification dispatched reminderId={} userId={} itemId={} date={}",
                        reminder.getId(), reminder.getUserId(), reminder.getItemId(), today);
            } catch (Exception exception) {
                failed++;
                logger.error("Failed to dispatch push notification for reminder " +
                        "reminderId={} userId={}: {}",
                        reminder.getId(), reminder.getUserId(), exception.getMessage());
                // Continue — per-reminder failure must not block other reminders
            }
        }

        logger.info("Reminder dispatch job finished date={} dispatched={} failed={}",
                today, dispatched, failed);
    }

    /**
     * Resolves the title of the item associated with a reminder.
     * Falls back to a generic title if the item is not found (e.g., was deleted).
     */
    private String resolveItemTitle(Long itemId) {
        if (itemId == null) {
            return "Saved Item";
        }
        Optional<Item> item = itemRepository.findById(itemId);
        if (item.isPresent() && item.get().getTitle() != null) {
            return item.get().getTitle();
        }
        return "Saved Item";
    }
}
