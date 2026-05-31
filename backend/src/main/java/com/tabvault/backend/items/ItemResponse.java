package com.tabvault.backend.items;

import java.time.OffsetDateTime;

/**
 * Response DTO for a saved item record.
 *
 * summary, suggestedCategory, and contentType may be null if LLM analysis has not yet completed.
 * noteBody is only present for NOTE type items.
 *
 * suggestedCategory and contentType are populated by MOD-003 (content analysis pipeline)
 * after the Claude API returns the analysis result (AC-010).
 */
public record ItemResponse(
        Long id,
        String itemType,
        String url,
        String title,
        String faviconUrl,
        String summary,
        String suggestedCategory,
        String contentType,
        String noteBody,
        Long categoryId,
        boolean pinned,
        boolean archived,
        OffsetDateTime lastVisitedAt,
        OffsetDateTime createdAt
) {
    static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getItemType().name(),
                item.getUrl(),
                item.getTitle(),
                item.getFaviconUrl(),
                item.getSummary(),
                item.getSuggestedCategory(),
                item.getContentType(),
                item.getNoteBody(),
                item.getCategoryId(),
                item.isPinned(),
                item.isArchived(),
                item.getLastVisitedAt(),
                item.getCreatedAt()
        );
    }
}
