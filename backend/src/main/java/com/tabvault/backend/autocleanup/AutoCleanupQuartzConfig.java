package com.tabvault.backend.autocleanup;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that registers the {@link AutoCleanupJob} Quartz JobDetail
 * and CronTrigger with the Quartz scheduler.
 *
 * Because {@code spring.quartz.job-store-type=jdbc} is set in application.properties,
 * both the JobDetail and CronTrigger are persisted to the {@code qrtz_*} JDBC tables
 * in PostgreSQL. The schedule survives service restarts.
 *
 * This satisfies the production.md Shared Convention:
 *   "The Quartz job store shall be configured as JDBC (PostgreSQL-backed) in all
 *   environments; the in-memory store shall not be used because it does not survive
 *   service restarts."
 *
 * In the test profile, {@code spring.quartz.job-store-type=memory} overrides the
 * JDBC store so unit tests do not require PostgreSQL access.
 *
 * AC-033, AC-034: daily job evaluates staleness and auto-archives items.
 */
@Configuration
public class AutoCleanupQuartzConfig {

    private static final Logger logger = LoggerFactory.getLogger(AutoCleanupQuartzConfig.class);

    /** Cron expression for the auto-cleanup job — read from env var. */
    private final String cleanupCron;

    public AutoCleanupQuartzConfig(
            @Value("${app.autocleanup.cron:0 0 9 * * ?}") String cleanupCron) {
        this.cleanupCron = cleanupCron;
        logger.info("AutoCleanup Quartz job cron configured expression='{}'", cleanupCron);
    }

    /**
     * JobDetail for the auto-cleanup job.
     *
     * {@code storeDurably(true)} means the JobDetail survives in the JDBC store
     * even when no trigger is currently associated with it.
     *
     * @return the Quartz JobDetail for {@link AutoCleanupJob}
     */
    @Bean
    public JobDetail autoCleanupJobDetail() {
        return JobBuilder.newJob(AutoCleanupJob.class)
                .withIdentity("autoCleanupJob", "autocleanup")
                .withDescription("Daily job: creates staleness reminders and auto-archives items " +
                        "that passed the grace period without user interaction (MOD-006)")
                .storeDurably(true)
                .build();
    }

    /**
     * CronTrigger that fires the auto-cleanup job on the configured cron schedule.
     *
     * The trigger is persisted in the Quartz JDBC store alongside the JobDetail.
     * Uses {@code withMisfireHandlingInstructionFireAndProceed()} so that if the
     * scheduler was down when the trigger should have fired, it fires once immediately
     * on recovery (catch-up semantics).
     *
     * @param autoCleanupJobDetail the JobDetail for the auto-cleanup job
     * @return the Quartz CronTrigger
     */
    @Bean
    public Trigger autoCleanupTrigger(JobDetail autoCleanupJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(autoCleanupJobDetail)
                .withIdentity("autoCleanupTrigger", "autocleanup")
                .withDescription("Cron trigger for daily auto-cleanup job (MOD-006)")
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(cleanupCron)
                                .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
