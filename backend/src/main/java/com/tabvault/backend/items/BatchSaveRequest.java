package com.tabvault.backend.items;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for saving all open tabs in the current browser window.
 *
 * AC-005: An array of tab objects (url + title).
 * AC-065: Rate-limited to 100 URLs per rolling 60-minute window per user.
 */
public record BatchSaveRequest(

        @NotEmpty(message = "At least one tab is required")
        @Size(max = 500, message = "Batch request must not exceed 500 tabs")
        @Valid
        List<SaveItemRequest> tabs
) {}
