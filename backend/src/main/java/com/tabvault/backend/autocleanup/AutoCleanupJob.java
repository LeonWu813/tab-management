package com.tabvault.backend.autocleanup;

import com.tabvault.backend.auth.UserRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Quartz {@link Job} that runs the daily auto-cleanup evaluation for all users.
 *
 * This class implements {@code org.quartz.Job} so the trigger is stored in the Quartz
 * JDBC job store (PostgreSQL-backed per production.md Shared Convention). The job
 * persists across service restarts — if the service restarts mid-day, Quartz recovers
 * the trigger on startup and fires it if it was missed.
 *
 * The companion {@link AutoCleanupQuartzConfig} registers the JobDetail and CronTrigger
 * at application startup. Spring Boot's {@code SpringBeanJobFactory} auto-configuration
 * ensures constructor injection works correctly.
 *
 * AC-033: creates staleness reminders for stale non-pinned, non-archived items.
 * AC-034: auto-archives items that passed the grace period without a visit.
 * AC-039: skips users who have opted out of auto-cleanup.
 * AC-066: idempotent — does not duplicate pending staleness reminders.
 *
 * Production.md Shared Convention:
 *   "The Quartz job store shall be configured as JDBC (PostgreSQL-backed) in all
 *   environments; the in-memory store shall not be used because it does not survive
 *   service restarts."
 */
@Component
public class AutoCleanupJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(AutoCleanupJob.class);

    private final AutoCleanupService autoCleanupService;
    private final UserRepository userRepository;

    /**
     * Spring injects dependencies via constructor injection.
     * Quartz uses the Spring-managed instance via SpringBeanJobFactory.
     *
     * @param autoCleanupService the cleanup service containing the core business logic
     * @param userRepository     repository for retrieving all registered user IDs
     */
    public AutoCleanupJob(
            AutoCleanupService autoCleanupService,
            UserRepository userRepository) {
        this.autoCleanupService = autoCleanupService;
        this.userRepository = userRepository;
    }

    /**
     * Quartz entry point: evaluates all users and runs the daily staleness check.
     *
     * Retrieves the list of all registered user IDs and delegates per-user processing
     * to {@link AutoCleanupService#runDailyCleanup(List)}. Per-user failures are
     * isolated within the service — one user's failure does not block others.
     *
     * AC-033, AC-034, AC-039, AC-066
     *
     * @param context Quartz job execution context (not used; all state is in the DB)
     * @throws JobExecutionException if a fatal, non-recoverable error occurs
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.info("AutoCleanupJob: daily auto-cleanup job triggered");

        try {
            // Retrieve all user IDs — the service handles per-user opt-out checks
            List<Long> userIds = userRepository.findAll()
                    .stream()
                    .map(user -> user.getId())
                    .toList();

            logger.info("AutoCleanupJob: processing users count={}", userIds.size());
            autoCleanupService.runDailyCleanup(userIds);

        } catch (Exception exception) {
            logger.error("AutoCleanupJob: fatal error during daily cleanup: {}",
                    exception.getMessage(), exception);
            throw new JobExecutionException("Daily auto-cleanup job failed", exception, false);
        }
    }
}
