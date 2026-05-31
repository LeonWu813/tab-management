package com.tabvault.backend.contentanalysis;

/**
 * Thrown when the Claude API call fails (HTTP error, timeout, or response parse failure).
 *
 * The ContentAnalysisService catches this exception and increments the job's retry_count.
 * After MAX_RETRIES failed attempts, the job is marked FAILED and the item is left without a summary.
 */
public class ClaudeApiException extends RuntimeException {

    public ClaudeApiException(String message) {
        super(message);
    }

    public ClaudeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
