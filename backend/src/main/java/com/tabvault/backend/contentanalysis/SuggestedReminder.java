package com.tabvault.backend.contentanalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A suggested reminder created by the content analysis pipeline when the
 * extract_deadlines tool detects a time-sensitive date in the saved page content.
 *
 * Reminders start in PENDING_CONFIRMATION status and must be explicitly confirmed
 * by the user before push notifications are dispatched (AC-012, AC-013).
 */
@Entity
@Table(name = "suggested_reminders")
public class SuggestedReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "detected_date", nullable = false)
    private LocalDate detectedDate;

    @Column(name = "label", nullable = false, length = 500)
    private String label;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "urgency", nullable = false, columnDefinition = "urgency_level")
    private UrgencyLevel urgency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "reminder_status")
    private ReminderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    protected SuggestedReminder() {
        // Required by JPA
    }

    /**
     * Creates a new pending-confirmation reminder from a detected deadline.
     *
     * AC-012: Reminder status starts as PENDING_CONFIRMATION.
     */
    public SuggestedReminder(Long itemId, Long userId, LocalDate detectedDate,
                             String label, UrgencyLevel urgency) {
        this.itemId = itemId;
        this.userId = userId;
        this.detectedDate = detectedDate;
        this.label = label;
        this.urgency = urgency;
        this.status = ReminderStatus.PENDING_CONFIRMATION;
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getDetectedDate() {
        return detectedDate;
    }

    public String getLabel() {
        return label;
    }

    public UrgencyLevel getUrgency() {
        return urgency;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public void setStatus(ReminderStatus status) {
        this.status = status;
    }

    /**
     * Updates the due date of the reminder.
     * Used by MOD-005 when the user updates the due date of an existing reminder (AC-023).
     */
    public void setDetectedDate(java.time.LocalDate detectedDate) {
        this.detectedDate = detectedDate;
    }

    /**
     * Updates the label of the reminder.
     * Used by MOD-005 when the user updates the label of an existing reminder (AC-023).
     */
    public void setLabel(String label) {
        this.label = label;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
