package com.tabvault.backend.items;

/**
 * Status of a content analysis job in the outbox table.
 *
 * State transitions:
 *   PENDING -> PROCESSING -> COMPLETED
 *                        -> FAILED (retryable)
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
