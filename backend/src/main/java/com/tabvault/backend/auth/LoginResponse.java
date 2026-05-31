package com.tabvault.backend.auth;

/**
 * Response body for a successful POST /api/auth/login (HTTP 200).
 * Returns a JWT access token (15-minute expiry) and a refresh token (7-day expiry)
 * in the response body, per AC-046.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken
) {}
