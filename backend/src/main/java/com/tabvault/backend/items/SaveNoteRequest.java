package com.tabvault.backend.items;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for saving a plain text note.
 *
 * AC-025 / AC-026: noteBody is stored as-is, without modification or sanitization.
 */
public record SaveNoteRequest(

        @NotBlank(message = "Note body is required")
        String noteBody
) {}
