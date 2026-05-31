package com.tabvault.backend.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwtService — no Spring context, no database.
 */
class JwtServiceTest {

    // 128-char hex secret — satisfies HMAC-SHA256 key size requirement
    private static final String TEST_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" +
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 15L);
    }

    @Test
    @DisplayName("generateAccessToken produces a non-blank token")
    void generateAccessToken_producesNonBlankToken() {
        String token = jwtService.generateAccessToken(1L);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("extractUserId returns the userId embedded in the token")
    void extractUserId_returnsEmbeddedUserId() {
        Long userId = 42L;
        String token = jwtService.generateAccessToken(userId);
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("isAccessTokenValid returns true for a freshly generated token")
    void isAccessTokenValid_freshToken_returnsTrue() {
        String token = jwtService.generateAccessToken(7L);
        assertThat(jwtService.isAccessTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isAccessTokenValid returns false for a garbage string")
    void isAccessTokenValid_garbageToken_returnsFalse() {
        assertThat(jwtService.isAccessTokenValid("this-is-not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("parseAccessToken throws JwtException when token is signed with a different key")
    void parseAccessToken_wrongKey_throwsJwtException() {
        JwtService otherService = new JwtService(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                15L);
        String token = otherService.generateAccessToken(1L);

        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("isAccessTokenValid returns false for a token that expired immediately (0-minute expiry)")
    void isAccessTokenValid_expiredToken_returnsFalse() throws InterruptedException {
        // Token with 0-minute expiry expires as soon as it is created
        JwtService shortLivedService = new JwtService(TEST_SECRET, 0L);
        String token = shortLivedService.generateAccessToken(1L);
        // Allow a tick for expiry to take effect
        Thread.sleep(10);
        assertThat(shortLivedService.isAccessTokenValid(token)).isFalse();
    }
}
