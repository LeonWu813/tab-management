package com.tabvault.backend.items;

/**
 * Thrown when an item is not found or does not belong to the requesting user.
 * Maps to HTTP 404.
 */
public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(String message) {
        super(message);
    }
}
