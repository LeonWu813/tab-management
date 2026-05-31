package com.tabvault.backend.contentanalysis;

import java.time.LocalDate;
import java.util.List;

/**
 * Structured result from the Claude API tool-use analysis.
 *
 * Contains the summary, suggested category, content type, and any
 * detected deadlines returned by the extract_deadlines tool.
 *
 * AC-010: summary, suggestedCategory, and contentType are written to the item record.
 * AC-012: deadlines (if any) are used to create SuggestedReminder records.
 */
public record AnalysisResult(
        String summary,
        String suggestedCategory,
        String contentType,
        List<DetectedDeadline> deadlines
) {

    /**
     * A single deadline detected by the extract_deadlines tool.
     *
     * AC-012: Each deadline produces one SuggestedReminder with detected date, label, urgency.
     */
    public record DetectedDeadline(
            LocalDate date,
            String label,
            UrgencyLevel urgency
    ) {
    }
}
