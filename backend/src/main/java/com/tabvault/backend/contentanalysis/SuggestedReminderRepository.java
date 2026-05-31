package com.tabvault.backend.contentanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for SuggestedReminder entities.
 *
 * Used by ContentAnalysisService to write deadline reminders detected by
 * the extract_deadlines tool (AC-012).
 *
 * Also used by MOD-005 (Reminder Service) for CRUD operations on reminders
 * and for the daily notification dispatch scheduler (AC-022).
 */
public interface SuggestedReminderRepository extends JpaRepository<SuggestedReminder, Long> {

    /**
     * Returns all reminders for a given item (useful for integration checks).
     */
    List<SuggestedReminder> findByItemId(Long itemId);

    /**
     * Finds a single reminder by ID that belongs to the specified user.
     * Returns empty if the reminder does not exist or belongs to another user.
     *
     * AC-023: ownership check — only the reminder owner may update or dismiss.
     */
    Optional<SuggestedReminder> findByIdAndUserId(Long id, Long userId);

    /**
     * Returns all non-dismissed reminders for a given user, ordered by detected_date ascending.
     *
     * Used by MOD-005 to list the user's active reminders.
     */
    List<SuggestedReminder> findByUserIdAndStatusNotOrderByDetectedDateAsc(
            Long userId, ReminderStatus excludedStatus);

    /**
     * Returns all CONFIRMED reminders whose detected_date equals the given date.
     *
     * AC-022: used by the daily notification scheduler to find reminders due today.
     */
    @Query("SELECT r FROM SuggestedReminder r " +
           "WHERE r.status = 'CONFIRMED' " +
           "AND r.detectedDate = :dueDate")
    List<SuggestedReminder> findConfirmedRemindersDueOn(@Param("dueDate") LocalDate dueDate);
}
