package com.tabvault.backend.auth;

/**
 * Response body for a successful POST /api/auth/register (HTTP 201).
 * Returns the user's display name and a JWT access token.
 * Per spec: registration success returns the display name and an access token.
 * A refresh token is also issued to allow the client to stay logged in.
 */
public record RegisterResponse(
        String displayName,
        String accessToken,
        String refreshToken
) {}
