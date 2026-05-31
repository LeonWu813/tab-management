package com.tabvault.backend.items;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for saving a single browser tab (link/video).
 *
 * AC-001: url and title are required. faviconUrl is optional.
 */
public record SaveItemRequest(

        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL must not exceed 2048 characters")
        String url,

        @NotBlank(message = "Title is required")
        @Size(max = 1000, message = "Title must not exceed 1000 characters")
        String title,

        String faviconUrl
) {}
