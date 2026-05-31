package com.tabvault.backend.contentanalysis;

/**
 * Status of a suggested reminder record.
 *
 * A reminder starts as PENDING_CONFIRMATION when detected by the content analysis pipeline.
 * It must be explicitly confirmed by the user before push notifications are dispatched.
 *
 * AC-013: A pending-confirmation reminder shall not trigger push notifications.
 */
public enum ReminderStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    DISMISSED
}
