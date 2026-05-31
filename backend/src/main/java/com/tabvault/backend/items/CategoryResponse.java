package com.tabvault.backend.items;

import java.time.OffsetDateTime;

/**
 * Response DTO for a category record.
 */
public record CategoryResponse(
        Long id,
        String name,
        String color,
        String icon,
        int sortOrder,
        OffsetDateTime createdAt
) {
    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getIcon(),
                category.getSortOrder(),
                category.getCreatedAt()
        );
    }
}
