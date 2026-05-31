package com.tabvault.backend.items;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's @Async support for asynchronous batch-save processing.
 *
 * The thread pool is configured via spring.task.execution.pool.* in
 * application.properties. Spring Boot auto-configures a ThreadPoolTaskExecutor
 * with those settings when @EnableAsync is active.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // Spring Boot auto-configures the ThreadPoolTaskExecutor from
    // spring.task.execution.pool.core-size, max-size, and queue-capacity.
}
