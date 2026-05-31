package com.tabvault.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Manages opaque refresh tokens.
 *
 * Refresh tokens are random 256-bit values encoded as URL-safe Base64 strings.
 * Only the SHA-256 hash is persisted in the database — the raw value is
 * returned to the client once at issuance time and never stored.
 *
 * Token rotation: each call to rotateRefreshToken revokes the consumed token
 * and issues a new one, preventing reuse of a stolen token.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit random value

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiryDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.auth.jwt.refresh-token-expiry-days:7}") long refreshTokenExpiryDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
    }

    /**
     * Creates a new refresh token for the given user.
     * Persists the hash and returns the raw token value for transmission to the client.
     *
     * @param user the authenticated user
     * @return raw refresh token string (URL-safe Base64)
     */
    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(refreshTokenExpiryDays);

        RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt);
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    /**
     * Rotates a refresh token: revokes the existing token for the submitted raw value
     * and issues a new one for the same user.
     *
     * @param rawToken the raw refresh token submitted by the client
     * @return new raw refresh token value
     * @throws InvalidRefreshTokenException if the token is not found, revoked, or expired
     */
    @Transactional
    public RotationResult rotateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (existing.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }

        if (existing.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        existing.revoke();
        refreshTokenRepository.save(existing);

        User user = existing.getUser();
        String newRawToken = createRefreshToken(user);

        return new RotationResult(user, newRawToken);
    }

    /**
     * Revokes all active refresh tokens for the given user (logout).
     *
     * @param userId the user's database ID
     */
    @Transactional
    public void revokeAllTokensForUser(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Returns the SHA-256 hex digest of the raw token value.
     */
    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    /**
     * Carries the user and new refresh token out of a rotation transaction.
     */
    public record RotationResult(User user, String newRawToken) {}
}
