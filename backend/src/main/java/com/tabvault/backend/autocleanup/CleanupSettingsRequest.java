package com.tabvault.backend.autocleanup;

/**
 * Request body for updating auto-cleanup settings.
 *
 * Both fields are optional — only provided fields are applied. A null value
 * leaves the existing setting unchanged.
 *
 * AC-038: stalenessThresholdDays must be one of 14, 30, 60, or 90 when provided.
 * AC-039: autoCleanupEnabled=false disables staleness reminders and auto-archiving.
 */
public record CleanupSettingsRequest(
        /**
         * Staleness threshold in days. Allowed values: 14, 30, 60, 90.
         * Null means "no change".
         */
        Integer stalenessThresholdDays,

        /**
         * When false, the user opts out of all auto-cleanup behaviour.
         * Null means "no change".
         */
        Boolean autoCleanupEnabled
) {}
