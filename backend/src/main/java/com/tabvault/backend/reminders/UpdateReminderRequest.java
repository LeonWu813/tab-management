package com.tabvault.backend.reminders;

import jakarta.validation.constraints.Future;

import java.time.LocalDate;

/**
 * Request DTO for updating or dismissing an existing reminder.
 *
 * AC-023: user may update the due date and/or label, or dismiss the reminder.
 * - To dismiss: set dismissed = true (dueDate and label are ignored).
 * - To update: provide new dueDate and/or label (dismissed must be false or null).
 * - At least one of dismissed=true, dueDate, or label must be present.
 */
public record UpdateReminderRequest(

        /**
         * When true, the reminder is dismissed (status set to DISMISSED).
         * Takes precedence over dueDate/label updates.
         * AC-023: allows user to dismiss a reminder.
         */
        Boolean dismissed,

        /**
         * New due date for the reminder. Must be in the future when provided.
         * AC-023: allows user to update the reminder due date.
         */
        @Future(message = "Due date must be in the future")
        LocalDate dueDate,

        /**
         * New label for the reminder. Optional; null means no change.
         * AC-023: allows user to update the reminder label.
         */
        String label
) {
}
