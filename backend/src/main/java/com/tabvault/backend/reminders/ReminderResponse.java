package com.tabvault.backend.reminders;

import com.tabvault.backend.contentanalysis.ReminderStatus;
import com.tabvault.backend.contentanalysis.SuggestedReminder;
import com.tabvault.backend.contentanalysis.UrgencyLevel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for a reminder record.
 *
 * dueWithin24Hours is a computed field indicating whether this reminder is due within
 * the next 24 hours — used by the dashboard to display a badge indicator on the item card.
 *
 * AC-024: badge indicator when reminder is due within 24 hours.
 */
public record ReminderResponse(
        Long id,
        Long itemId,
        Long userId,
        LocalDate dueDate,
        String label,
        String urgency,
        String status,
        boolean dueWithin24Hours,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /**
     * Maps a SuggestedReminder entity to a response DTO.
     *
     * AC-024: dueWithin24Hours is set to true if the reminder's dueDate is today or tomorrow
     * within the 24-hour window from now.
     */
    static ReminderResponse from(SuggestedReminder reminder) {
        LocalDate today = LocalDate.now();
        LocalDate dueDate = reminder.getDetectedDate();
        boolean dueWithin24Hours = dueDate != null &&
                !dueDate.isBefore(today) &&
                !dueDate.isAfter(today.plusDays(1));

        return new ReminderResponse(
                reminder.getId(),
                reminder.getItemId(),
                reminder.getUserId(),
                dueDate,
                reminder.getLabel(),
                reminder.getUrgency() != null ? reminder.getUrgency().name() : null,
                reminder.getStatus() != null ? reminder.getStatus().name() : null,
                dueWithin24Hours,
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
