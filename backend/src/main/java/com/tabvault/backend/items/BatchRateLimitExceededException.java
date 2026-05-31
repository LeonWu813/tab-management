package com.tabvault.backend.items;

/**
 * Thrown when a user exceeds the batch-save rate limit.
 * AC-065: more than 100 tab URLs in a rolling 60-minute window.
 * Maps to HTTP 429.
 */
public class BatchRateLimitExceededException extends RuntimeException {

    public BatchRateLimitExceededException(String message) {
        super(message);
    }
}
