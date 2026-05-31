package com.tabvault.backend.reminders;

import com.tabvault.backend.contentanalysis.SuggestedReminder;
import com.tabvault.backend.contentanalysis.SuggestedReminderRepository;
import com.tabvault.backend.items.Item;
import com.tabvault.backend.items.ItemRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Quartz {@link Job} that evaluates CONFIRMED reminders daily and dispatches push
 * notifications when a reminder's due date matches today's date.
 *
 * This class implements {@code org.quartz.Job} so the trigger is stored in Quartz's
 * JDBC job store (PostgreSQL-backed). The job persists across service restarts —
 * if the service restarts mid-day, Quartz can detect and fire the missed trigger on
 * recovery, which a plain {@code @Scheduled} cron cannot do.
 *
 * The companion {@link QuartzConfig} registers the JobDetail and CronTrigger into
 * the scheduler at application startup. {@link ReminderScheduler} retains its
 * {@code @Scheduled} annotation for backwards compatibility and as a fallback.
 *
 * AC-022: dispatches push notifications to all registered subscriptions for the user
 *         when a reminder's due time is reached, containing the item title and label.
 *
 * Quartz Shared Convention (production.md):
 *   "The Quartz job store shall be configured as JDBC (PostgreSQL-backed) in all
 *   environments; the in-memory store shall not be used because it does not survive
 *   service restarts."
 */
@Component
public class ReminderDispatchJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(ReminderDispatchJob.class);

    private final SuggestedReminderRepository reminderRepository;
    private final ItemRepository itemRepository;
    private final PushNotificationService pushNotificationService;

    /**
     * Spring injects dependencies via constructor injection.
     * Quartz uses the Spring-managed instance because {@link QuartzConfig} sets
     * {@code jobDetail.setJobClass(ReminderDispatchJob.class)} and Spring Boot's
     * Quartz auto-configuration registers a {@code SpringBeanJobFactory} that
     * delegates job instantiation to the Spring application context.
     *
     * @param reminderRepository     repository for querying due reminders
     * @param itemRepository         repository for resolving item titles
     * @param pushNotificationService service for dispatching Web Push notifications
     */
    public ReminderDispatchJob(
            SuggestedReminderRepository reminderRepository,
            ItemRepository itemRepository,
            PushNotificationService pushNotificationService) {
        this.reminderRepository = reminderRepository;
        this.itemRepository = itemRepository;
        this.pushNotificationService = pushNotificationService;
    }

    /**
     * Quartz entry point: evaluates CONFIRMED reminders due today and dispatches
     * push notifications for each.
     *
     * Per-reminder dispatch failures are caught individually so that one failed
     * delivery does not block the remaining reminders.
     *
     * AC-022: notification contains item title and reminder label.
     *
     * @param context Quartz job execution context (not used; all state is in the DB)
     * @throws JobExecutionException if a fatal, non-recoverable error occurs
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        LocalDate today = LocalDate.now();
        logger.info("Quartz reminder dispatch job started date={}", today);

        List<SuggestedReminder> dueReminders = reminderRepository.findConfirmedRemindersDueOn(today);

        if (dueReminders.isEmpty()) {
            logger.info("No reminders due today; Quartz dispatch job finished date={}", today);
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

        logger.info("Quartz reminder dispatch job finished date={} dispatched={} failed={}",
                today, dispatched, failed);
    }

    /**
     * Resolves the title of the item associated with a reminder.
     * Falls back to a generic title if the item is not found (e.g., was deleted
     * after the reminder was created).
     *
     * @param itemId the item ID stored on the reminder; may be null
     * @return the item's title, or "Saved Item" as a fallback
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
