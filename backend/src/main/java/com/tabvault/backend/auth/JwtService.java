package com.tabvault.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Generates and validates JWT access tokens.
 *
 * Access tokens embed the user's ID as the "sub" claim.
 * They expire after the configured number of minutes (default: 15).
 *
 * The signing secret is injected from the JWT_SECRET environment variable.
 * Refresh tokens are opaque random strings managed by RefreshTokenService —
 * this class handles only the signed JWT access tokens.
 */
@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long accessTokenExpiryMinutes;

    public JwtService(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.access-token-expiry-minutes:15}") long accessTokenExpiryMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
    }

    /**
     * Generates a signed JWT access token embedding the userId as the subject.
     *
     * @param userId the user's database ID
     * @return a compact signed JWT string
     */
    public String generateAccessToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenExpiryMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses a JWT access token and returns its claims.
     * Throws JwtException if the token is invalid or expired.
     *
     * @param token compact JWT string
     * @return parsed claims
     * @throws JwtException if parsing or signature validation fails
     */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the user ID from a valid JWT access token.
     *
     * @param token compact JWT string
     * @return user ID from the subject claim
     * @throws JwtException if the token is invalid or expired
     */
    public Long extractUserId(String token) {
        Claims claims = parseAccessToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Returns true if the token can be parsed and has not expired.
     */
    public boolean isAccessTokenValid(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            logger.debug("JWT validation failed", exception);
            return false;
        }
    }
}
