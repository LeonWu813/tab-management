package com.tabvault.backend.items;

/**
 * Thrown when a category is not found or does not belong to the requesting user.
 * Maps to HTTP 404.
 */
public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String message) {
        super(message);
    }
}
