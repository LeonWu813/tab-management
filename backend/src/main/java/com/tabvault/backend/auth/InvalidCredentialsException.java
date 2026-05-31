package com.tabvault.backend.auth;

/**
 * Thrown when login credentials are invalid (email not found or wrong password).
 * The message is intentionally generic — it must not reveal whether the account
 * exists or the password is incorrect (AC-047).
 * Handled by AuthExceptionHandler to return HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
