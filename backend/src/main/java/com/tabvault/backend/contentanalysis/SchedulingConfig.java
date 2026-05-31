package com.tabvault.backend.contentanalysis;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled support for the content analysis pipeline polling loop.
 *
 * The @Scheduled(fixedDelayString = ...) annotation on ContentAnalysisService.processPendingJobs()
 * requires @EnableScheduling to be active. This configuration class activates it.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // No additional beans — Spring Boot auto-configures the task scheduler.
}
