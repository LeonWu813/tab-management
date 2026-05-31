package com.tabvault.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RefreshTokenService — repository is mocked.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 7L);
        testUser = new User("test@example.com", "hashed", "Test User");
    }

    @Test
    @DisplayName("createRefreshToken returns a non-blank raw token and persists a hash")
    void createRefreshToken_persistsHashNotRawValue() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(testUser);

        assertThat(rawToken).isNotBlank();
        // Raw token is URL-safe Base64; it should not contain + or /
        assertThat(rawToken).doesNotContain("+", "/");
    }

    @Test
    @DisplayName("hashToken is deterministic for the same input")
    void hashToken_isDeterministic() {
        String hash1 = RefreshTokenService.hashToken("my-raw-token");
        String hash2 = RefreshTokenService.hashToken("my-raw-token");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("hashToken produces different outputs for different inputs")
    void hashToken_differentInputs_differentOutputs() {
        String hash1 = RefreshTokenService.hashToken("token-a");
        String hash2 = RefreshTokenService.hashToken("token-b");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("rotateRefreshToken revokes the old token and returns a new token with the same user")
    void rotateRefreshToken_validToken_rotatesSuccessfully() {
        String rawToken = "valid-raw-token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        RefreshToken existing = new RefreshToken(testUser, tokenHash, OffsetDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(rawToken);

        assertThat(existing.isRevoked()).isTrue();
        assertThat(result.user()).isSameAs(testUser);
        assertThat(result.newRawToken()).isNotBlank();
        assertThat(result.newRawToken()).isNotEqualTo(rawToken);
    }

    @Test
    @DisplayName("rotateRefreshToken throws InvalidRefreshTokenException when token is not found")
    void rotateRefreshToken_tokenNotFound_throwsException() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("rotateRefreshToken throws InvalidRefreshTokenException when token is already revoked")
    void rotateRefreshToken_revokedToken_throwsException() {
        String rawToken = "revoked-token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        RefreshToken revoked = new RefreshToken(testUser, tokenHash, OffsetDateTime.now().plusDays(7));
        revoked.revoke();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("rotateRefreshToken throws InvalidRefreshTokenException when token is expired")
    void rotateRefreshToken_expiredToken_throwsException() {
        String rawToken = "expired-token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        RefreshToken expired = new RefreshToken(testUser, tokenHash, OffsetDateTime.now().minusDays(1));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("revokeAllTokensForUser delegates to repository with the correct userId")
    void revokeAllTokensForUser_callsRepository() {
        refreshTokenService.revokeAllTokensForUser(99L);
        verify(refreshTokenRepository).revokeAllByUserId(99L);
    }
}
