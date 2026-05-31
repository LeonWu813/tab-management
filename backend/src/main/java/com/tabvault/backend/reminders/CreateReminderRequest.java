package com.tabvault.backend.reminders;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for creating a manual reminder on a saved item.
 *
 * AC-021: item ID must be owned by the authenticated user; due date must be a future date;
 * label is optional.
 */
public record CreateReminderRequest(

        /**
         * The ID of the saved item this reminder is associated with.
         * Must be owned by the authenticated user.
         */
        @NotNull(message = "Item ID is required")
        Long itemId,

        /**
         * The due date for the reminder. Must be a future date.
         * AC-021: valid future due date required.
         */
        @NotNull(message = "Due date is required")
        @Future(message = "Due date must be in the future")
        LocalDate dueDate,

        /**
         * Optional human-readable label describing what action is due.
         * When null or blank, a default label will be applied.
         */
        String label
) {
}
