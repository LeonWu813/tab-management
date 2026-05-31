package com.tabvault.backend.items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Enforces the per-user batch-save rate limit using a Redis sliding-window counter.
 *
 * AC-065: Rejects a batch save request with HTTP 429 when the requesting user
 * has submitted more than {@code maxUrls} tab URLs within the current rolling
 * {@code windowMinutes}-minute window.
 *
 * The Redis key is: "batch_rate_limit:{userId}".
 * Each key stores an integer count of URLs submitted in the current window.
 * The key's TTL is set to windowMinutes on first increment (sliding window).
 * If Redis is unavailable, the request is allowed through and the error is logged.
 */
@Service
public class BatchRateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(BatchRateLimitService.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "batch_rate_limit:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.items.batch-rate-limit.max-urls:100}")
    private int maxUrls;

    @Value("${app.items.batch-rate-limit.window-minutes:60}")
    private int windowMinutes;

    public BatchRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks whether the user has exceeded the rate limit for the current window.
     * If within the limit, increments the counter atomically.
     *
     * @param userId        the authenticated user ID
     * @param urlCount      the number of URLs in the current request
     * @throws BatchRateLimitExceededException if the limit would be exceeded
     */
    public void checkAndIncrement(Long userId, int urlCount) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        try {
            Long currentCount = redisTemplate.opsForValue().increment(key, 0);
            if (currentCount == null) {
                currentCount = 0L;
            }

            if (currentCount + urlCount > maxUrls) {
                logger.warn("Batch save rate limit exceeded userId={} currentCount={} requested={}",
                        userId, currentCount, urlCount);
                throw new BatchRateLimitExceededException(
                        "Batch save rate limit exceeded. You may submit at most " + maxUrls
                        + " URLs per " + windowMinutes + " minutes.");
            }

            Long newCount = redisTemplate.opsForValue().increment(key, urlCount);
            // Set TTL only when the key was just created (newCount equals urlCount)
            if (newCount != null && newCount == urlCount) {
                redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
            }
        } catch (BatchRateLimitExceededException exception) {
            throw exception;
        } catch (Exception exception) {
            // Redis unavailable — allow the request through and log the failure
            logger.error("Redis unavailable for rate limit check userId={}, allowing request through",
                    userId, exception);
        }
    }
}
