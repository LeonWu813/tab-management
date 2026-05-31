package com.tabvault.backend.items;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new category.
 *
 * AC-018: name must be 1–50 characters; color must be a valid hex color code
 * (e.g. #ff5733). icon is optional.
 */
public record CreateCategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(min = 1, max = 50, message = "Category name must be between 1 and 50 characters")
        String name,

        @NotBlank(message = "Color is required")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$",
                 message = "Color must be a valid hex color code (e.g. #ff5733)")
        String color,

        String icon
) {}
