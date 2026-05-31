package com.tabvault.backend.autocleanup;

/**
 * Thrown when a staleness threshold value is not one of the allowed values
 * (14, 30, 60, or 90 days).
 *
 * AC-038: the system shall apply an updated threshold only when the value is one of
 * the allowed values; otherwise this exception causes HTTP 400 to be returned.
 */
public class InvalidStalenessThresholdException extends RuntimeException {

    public InvalidStalenessThresholdException(int providedValue) {
        super("Invalid staleness threshold: " + providedValue +
                ". Allowed values are 14, 30, 60, or 90 days.");
    }
}
