package com.tabvault.backend.contentanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // -------------------------------------------------------------------------
    // MOD-006: Auto-Cleanup queries
    // -------------------------------------------------------------------------

    /**
     * Returns reminders with PENDING or PENDING_CONFIRMATION status for the given item.
     *
     * AC-066 (idempotency): the daily cleanup job must not create a new staleness reminder
     * if one already exists in a pending state for the item.
     *
     * @param itemId         the item whose pending staleness reminders are checked
     * @param pendingStatus  PENDING or PENDING_CONFIRMATION status value
     * @return list of pending reminders for the item
     */
    @Query("SELECT r FROM SuggestedReminder r " +
           "WHERE r.itemId = :itemId " +
           "AND r.status = :pendingStatus")
    List<SuggestedReminder> findByItemIdAndStatus(
            @Param("itemId") Long itemId,
            @Param("pendingStatus") ReminderStatus pendingStatus);

    /**
     * Returns DISMISSED reminders for the given item whose updated_at is older than
     * the provided cutoff date. These are candidates for auto-archiving: the reminder
     * was dismissed without the user selecting "Keep" or visiting the item, and the
     * 7-day grace period has elapsed.
     *
     * AC-034: auto-archive 7 days after staleness reminder is dismissed without a visit.
     *
     * @param itemId  the item's ID
     * @param cutoff  reminders last updated before this date are eligible for archiving
     * @return list of dismissed reminders that have passed the grace period
     */
    @Query("SELECT r FROM SuggestedReminder r " +
           "WHERE r.itemId = :itemId " +
           "AND r.status = 'DISMISSED' " +
           "AND r.updatedAt < :cutoff")
    List<SuggestedReminder> findDismissedRemindersBefore(
            @Param("itemId") Long itemId,
            @Param("cutoff") java.time.OffsetDateTime cutoff);

    /**
     * Deletes all reminders whose status is PENDING (staleness reminders created by the
     * auto-cleanup job) for a given item. Called when the user visits the item so that
     * the staleness signal is cleared.
     *
     * AC-035: clear pending staleness reminder when the user opens the original URL.
     *
     * @param itemId the item whose pending staleness reminders are to be deleted
     */
    @Modifying
    @Query("DELETE FROM SuggestedReminder r " +
           "WHERE r.itemId = :itemId " +
           "AND r.status = 'PENDING'")
    void deletePendingStalenessRemindersForItem(@Param("itemId") Long itemId);

    /**
     * Returns all reminders with PENDING status for a given user.
     * Used by the daily cleanup job to bulk-check staleness reminders.
     *
     * @param userId the user whose PENDING staleness reminders are returned
     * @return list of PENDING reminders for the user
     */
    @Query("SELECT r FROM SuggestedReminder r " +
           "WHERE r.userId = :userId " +
           "AND r.status = 'PENDING'")
    List<SuggestedReminder> findPendingStalenessRemindersForUser(@Param("userId") Long userId);
}
