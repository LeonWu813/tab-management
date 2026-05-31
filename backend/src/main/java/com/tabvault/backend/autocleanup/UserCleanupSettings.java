package com.tabvault.backend.autocleanup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Per-user auto-cleanup settings.
 *
 * One row exists per user. Created on first access with default values (staleness threshold
 * 30 days, auto-cleanup enabled). Users can update via {@link AutoCleanupSettingsController}.
 *
 * AC-038: staleness threshold may be 14, 30, 60, or 90 days.
 * AC-039: when auto_cleanup_enabled is false, no staleness reminders are created and no
 *         items are auto-archived for this user.
 */
@Entity
@Table(name = "user_cleanup_settings")
public class UserCleanupSettings {

    /** Default staleness threshold applied when no user-specific settings exist (days). */
    static final int DEFAULT_STALENESS_THRESHOLD_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * Number of days without a visit before a staleness reminder is created.
     * Allowed values: 14, 30, 60, 90 (enforced by {@link AutoCleanupService}).
     */
    @Column(name = "staleness_threshold_days", nullable = false)
    private int stalenessThresholdDays;

    /**
     * When false, the daily cleanup job skips this user entirely (opt-out).
     * AC-039: no staleness reminders created and no auto-archiving performed.
     */
    @Column(name = "auto_cleanup_enabled", nullable = false)
    private boolean autoCleanupEnabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    protected UserCleanupSettings() {
        // Required by JPA
    }

    /**
     * Creates a new settings record with default values for a user.
     *
     * @param userId the user whose settings these are
     */
    public UserCleanupSettings(Long userId) {
        this.userId = userId;
        this.stalenessThresholdDays = DEFAULT_STALENESS_THRESHOLD_DAYS;
        this.autoCleanupEnabled = true;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getStalenessThresholdDays() {
        return stalenessThresholdDays;
    }

    public void setStalenessThresholdDays(int stalenessThresholdDays) {
        this.stalenessThresholdDays = stalenessThresholdDays;
    }

    public boolean isAutoCleanupEnabled() {
        return autoCleanupEnabled;
    }

    public void setAutoCleanupEnabled(boolean autoCleanupEnabled) {
        this.autoCleanupEnabled = autoCleanupEnabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
