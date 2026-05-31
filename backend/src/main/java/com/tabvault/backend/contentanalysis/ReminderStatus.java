package com.tabvault.backend.contentanalysis;

/**
 * Status of a suggested reminder record.
 *
 * A reminder starts as PENDING_CONFIRMATION when detected by the content analysis pipeline.
 * It must be explicitly confirmed by the user before push notifications are dispatched.
 *
 * Staleness reminders created by the auto-cleanup scheduler (MOD-006) start as PENDING.
 * These are surfaced to the user as a "still need it?" prompt. If the user dismisses
 * the staleness reminder without visiting the item, the item is auto-archived after 7 days
 * (grace period).
 *
 * AC-013: A PENDING_CONFIRMATION reminder shall not trigger push notifications.
 * AC-033: Auto-cleanup staleness reminders start as PENDING.
 * AC-066: The daily cleanup job shall not create a new reminder if one with PENDING or
 *         PENDING_CONFIRMATION status already exists for the item.
 */
public enum ReminderStatus {
    /** Initial status for AI-detected deadline reminders; requires user confirmation. */
    PENDING_CONFIRMATION,
    /** Confirmed by the user; eligible for push notification dispatch. */
    CONFIRMED,
    /** Dismissed by the user. */
    DISMISSED,
    /** Initial status for auto-cleanup staleness reminders created by MOD-006. */
    PENDING
}
