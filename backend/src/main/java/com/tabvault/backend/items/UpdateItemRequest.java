package com.tabvault.backend.items;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for a partial update of a saved item's editable fields.
 *
 * All fields are optional. Only fields present (non-null) in the request body are applied.
 * At least one field must be non-null; the controller validates this and returns HTTP 400
 * if all three are null.
 *
 * Used by: PATCH /api/items/{id}
 *
 * Rules:
 * - title: optional; if present, must be 1–1000 characters
 * - summary: optional; if present, replaces the current summary (set to null to clear)
 * - categoryId: optional; if present, reassigns the item's category (null = uncategorized)
 */
public record UpdateItemRequest(

        @Size(min = 1, max = 1000, message = "Title must be between 1 and 1000 characters")
        String title,

        String summary,

        Long categoryId
) {
}
