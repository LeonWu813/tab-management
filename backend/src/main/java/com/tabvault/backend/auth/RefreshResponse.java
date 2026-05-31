package com.tabvault.backend.auth;

/**
 * Response body for a successful POST /api/auth/refresh (HTTP 200).
 * Returns a new JWT access token and a new refresh token (rotation).
 */
public record RefreshResponse(
        String accessToken,
        String refreshToken
) {}
