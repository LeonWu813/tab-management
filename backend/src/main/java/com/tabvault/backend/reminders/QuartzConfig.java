package com.tabvault.backend.reminders;

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
 * Spring configuration that registers the {@link ReminderDispatchJob} Quartz
 * JobDetail and CronTrigger with the Quartz scheduler.
 *
 * Because {@code spring.quartz.job-store-type=jdbc} is set in
 * {@code application.properties}, both the JobDetail and CronTrigger are
 * persisted to the {@code qrtz_*} JDBC tables in PostgreSQL. If the service
 * restarts, the scheduler recovers the job from the JDBC store and fires any
 * missed triggers on resume.
 *
 * This satisfies the production.md Shared Convention:
 *   "The Quartz job store shall be configured as JDBC (PostgreSQL-backed) in all
 *   environments; the in-memory store shall not be used because it does not
 *   survive service restarts."
 *
 * In the test profile, {@code spring.quartz.job-store-type=memory} overrides the
 * JDBC store so unit tests do not require PostgreSQL access.
 */
@Configuration
public class QuartzConfig {

    private static final Logger logger = LoggerFactory.getLogger(QuartzConfig.class);

    /** Cron expression for the reminder dispatch job — read from env var. */
    private final String dispatchCron;

    public QuartzConfig(
            @Value("${app.reminders.dispatch-cron:0 0 8 * * ?}") String dispatchCron) {
        this.dispatchCron = dispatchCron;
        logger.info("Quartz reminder dispatch cron configured expression='{}'", dispatchCron);
    }

    /**
     * JobDetail for the reminder dispatch job.
     *
     * {@code storeDurably(true)} means the JobDetail survives in the JDBC store
     * even if no trigger is currently associated with it (prevents orphan job
     * warnings on scheduler startup when the trigger exists in the store).
     *
     * @return the Quartz JobDetail for {@link ReminderDispatchJob}
     */
    @Bean
    public JobDetail reminderDispatchJobDetail() {
        return JobBuilder.newJob(ReminderDispatchJob.class)
                .withIdentity("reminderDispatchJob", "reminders")
                .withDescription("Daily job: evaluates CONFIRMED reminders due today and " +
                        "dispatches push notifications (AC-022, MOD-005)")
                .storeDurably(true)
                .build();
    }

    /**
     * CronTrigger that fires the reminder dispatch job on the configured cron schedule.
     *
     * The trigger is stored in the Quartz JDBC store alongside the JobDetail, making
     * the schedule persistent across service restarts.
     *
     * Uses {@code withMisfireHandlingInstructionFireAndProceed()} so that if the
     * scheduler was down when the trigger should have fired, it fires once
     * immediately on recovery (catch-up semantics).
     *
     * @param reminderDispatchJobDetail the JobDetail for the dispatch job
     * @return the Quartz CronTrigger
     */
    @Bean
    public Trigger reminderDispatchTrigger(JobDetail reminderDispatchJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(reminderDispatchJobDetail)
                .withIdentity("reminderDispatchTrigger", "reminders")
                .withDescription("Cron trigger for daily reminder dispatch job (MOD-005)")
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(dispatchCron)
                                .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
