package com.tabvault.backend.autocleanup;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for auto-cleanup settings (MOD-006).
 *
 * Endpoints:
 * <ul>
 *   <li>{@code GET  /api/cleanup-settings} — returns the authenticated user's settings</li>
 *   <li>{@code PUT  /api/cleanup-settings} — updates the authenticated user's settings</li>
 * </ul>
 *
 * AC-038: user can change staleness threshold to 14, 30, 60, or 90 days.
 * AC-039: user can disable auto-cleanup via the opt-out toggle.
 *
 * All endpoints require authentication. The security principal is the {@code Long userId}
 * set by {@link com.tabvault.backend.auth.JwtAuthenticationFilter}.
 */
@RestController
@RequestMapping("/api/cleanup-settings")
public class AutoCleanupSettingsController {

    private final AutoCleanupService autoCleanupService;

    public AutoCleanupSettingsController(AutoCleanupService autoCleanupService) {
        this.autoCleanupService = autoCleanupService;
    }

    /**
     * Returns the authenticated user's auto-cleanup settings.
     *
     * If the user has never configured cleanup settings, returns the defaults:
     * staleness threshold 30 days and auto-cleanup enabled.
     *
     * AC-038: response includes the current staleness threshold value.
     * AC-039: response includes the current opt-out state.
     *
     * @param userId the authenticated user's ID (injected from JWT principal)
     * @return HTTP 200 with the current settings
     */
    @GetMapping
    public ResponseEntity<CleanupSettingsResponse> getSettings(
            @AuthenticationPrincipal Long userId) {
        CleanupSettingsResponse response = autoCleanupService.getSettings(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates the authenticated user's auto-cleanup settings.
     *
     * Only non-null fields in the request body are applied; null fields leave the
     * existing setting unchanged.
     *
     * AC-038: {@code stalenessThresholdDays} must be 14, 30, 60, or 90 — returns
     *         HTTP 400 with {@code INVALID_STALENESS_THRESHOLD} if an invalid value
     *         is provided.
     * AC-039: {@code autoCleanupEnabled=false} opts the user out of all auto-cleanup.
     *
     * @param userId  the authenticated user's ID (injected from JWT principal)
     * @param request the settings update request
     * @return HTTP 200 with the updated settings
     */
    @PutMapping
    public ResponseEntity<CleanupSettingsResponse> updateSettings(
            @AuthenticationPrincipal Long userId,
            @RequestBody CleanupSettingsRequest request) {
        CleanupSettingsResponse response = autoCleanupService.updateSettings(userId, request);
        return ResponseEntity.ok(response);
    }
}
