package com.tabvault.backend.items;

/**
 * Request body for reassigning an item to a different category.
 *
 * AC-020: targetCategoryId may be null to move the item to "uncategorized".
 */
public record ReassignCategoryRequest(
        Long targetCategoryId
) {}
