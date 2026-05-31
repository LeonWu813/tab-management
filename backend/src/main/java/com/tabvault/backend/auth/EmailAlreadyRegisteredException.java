package com.tabvault.backend.auth;

/**
 * Thrown when a registration request uses an email already associated with
 * an existing account. Handled by AuthExceptionHandler to return HTTP 409.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
