package com.tabvault.backend.reminders;

/**
 * Thrown when a reminder is not found or does not belong to the requesting user.
 * Mapped to HTTP 404 by ReminderExceptionHandler.
 */
public class ReminderNotFoundException extends RuntimeException {

    public ReminderNotFoundException(Long reminderId) {
        super("Reminder not found: " + reminderId);
    }
}
