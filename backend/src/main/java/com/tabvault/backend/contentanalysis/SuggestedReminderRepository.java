package com.tabvault.backend.contentanalysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for SuggestedReminder entities.
 *
 * Used by ContentAnalysisService to write deadline reminders detected by
 * the extract_deadlines tool (AC-012).
 */
public interface SuggestedReminderRepository extends JpaRepository<SuggestedReminder, Long> {

    /**
     * Returns all reminders for a given item (useful for integration checks).
     */
    List<SuggestedReminder> findByItemId(Long itemId);
}
