package com.tabvault.backend.autocleanup;

import java.time.OffsetDateTime;

/**
 * Response DTO for auto-cleanup settings.
 *
 * AC-038: reflects the current staleness threshold (14, 30, 60, or 90 days).
 * AC-039: reflects the current opt-out state.
 */
public record CleanupSettingsResponse(
        Long userId,
        int stalenessThresholdDays,
        boolean autoCleanupEnabled,
        OffsetDateTime updatedAt
) {

    /**
     * Converts a {@link UserCleanupSettings} entity to a response DTO.
     *
     * @param settings the settings entity
     * @return the response DTO
     */
    public static CleanupSettingsResponse from(UserCleanupSettings settings) {
        return new CleanupSettingsResponse(
                settings.getUserId(),
                settings.getStalenessThresholdDays(),
                settings.isAutoCleanupEnabled(),
                settings.getUpdatedAt()
        );
    }
}
