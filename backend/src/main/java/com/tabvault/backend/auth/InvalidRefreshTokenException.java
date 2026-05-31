package com.tabvault.backend.auth;

/**
 * Thrown when a submitted refresh token is not found, has been revoked,
 * or has expired. Handled by AuthExceptionHandler to return HTTP 401.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
