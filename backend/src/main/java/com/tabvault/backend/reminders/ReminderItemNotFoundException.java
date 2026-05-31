package com.tabvault.backend.reminders;

/**
 * Thrown when creating a reminder for an item that does not exist or is not
 * owned by the requesting user.
 * Mapped to HTTP 404 by ReminderExceptionHandler.
 */
public class ReminderItemNotFoundException extends RuntimeException {

    public ReminderItemNotFoundException(Long itemId) {
        super("Item not found or not owned by user: " + itemId);
    }
}
