package com.tabvault.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthService — dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
    }

    // -------------------------------------------------------------------------
    // register()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("register returns HTTP 201 payload with displayName and accessToken on valid input")
    void register_validInput_returnsRegisterResponse() {
        // Arrange
        RegisterRequest request = new RegisterRequest("alice@example.com", "password123", "Alice");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return u;
        });
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any())).thenReturn("refresh-token");

        // Act
        RegisterResponse response = authService.register(request);

        // Assert
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("register throws EmailAlreadyRegisteredException when email already in use")
    void register_duplicateEmail_throwsEmailAlreadyRegisteredException() {
        // Arrange
        RegisterRequest request = new RegisterRequest("alice@example.com", "password123", "Alice");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register stores a BCrypt hash, not the plaintext password")
    void register_storesHashedPassword() {
        // Arrange
        RegisterRequest request = new RegisterRequest("bob@example.com", "mySecret99", "Bob");
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(jwtService.generateAccessToken(any())).thenReturn("token");
        when(refreshTokenService.createRefreshToken(any())).thenReturn("refresh");

        User[] savedUser = new User[1];
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            savedUser[0] = invocation.getArgument(0);
            return savedUser[0];
        });

        // Act
        authService.register(request);

        // Assert — saved password must not equal the plaintext value
        assertThat(savedUser[0].getPassword()).isNotEqualTo("mySecret99");
        assertThat(passwordEncoder.matches("mySecret99", savedUser[0].getPassword())).isTrue();
    }

    // -------------------------------------------------------------------------
    // login()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("login returns accessToken and refreshToken on valid credentials")
    void login_validCredentials_returnsLoginResponse() {
        // Arrange
        String rawPassword = "correct-password";
        String hashed = passwordEncoder.encode(rawPassword);
        User user = new User("charlie@example.com", hashed, "Charlie");

        LoginRequest request = new LoginRequest("charlie@example.com", rawPassword);
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any())).thenReturn("refresh-token");

        // Act
        LoginResponse response = authService.login(request);

        // Assert
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("login throws InvalidCredentialsException when email not found")
    void login_unknownEmail_throwsInvalidCredentialsException() {
        // Arrange
        LoginRequest request = new LoginRequest("nobody@example.com", "anypassword");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login throws InvalidCredentialsException when password is wrong")
    void login_wrongPassword_throwsInvalidCredentialsException() {
        // Arrange
        User user = new User("dave@example.com", passwordEncoder.encode("correct"), "Dave");
        LoginRequest request = new LoginRequest("dave@example.com", "wrong-password");
        when(userRepository.findByEmail("dave@example.com")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login InvalidCredentialsException message does not reveal whether account exists")
    void login_invalidCredentials_messageIsGeneric() {
        // Arrange
        LoginRequest request = new LoginRequest("nobody@example.com", "anypassword");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        // Act & Assert — message must not hint at account existence
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    // -------------------------------------------------------------------------
    // refresh()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("refresh returns new accessToken and refreshToken on valid refresh token")
    void refresh_validToken_returnsRefreshResponse() {
        // Arrange
        User user = new User("eve@example.com", "hashed", "Eve");
        RefreshTokenService.RotationResult rotationResult =
                new RefreshTokenService.RotationResult(user, "new-refresh-token");

        when(refreshTokenService.rotateRefreshToken("valid-refresh")).thenReturn(rotationResult);
        when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");

        // Act
        RefreshResponse response = authService.refresh("valid-refresh");

        // Assert
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("refresh propagates InvalidRefreshTokenException from rotation")
    void refresh_invalidToken_propagatesException() {
        // Arrange
        when(refreshTokenService.rotateRefreshToken("bad-token"))
                .thenThrow(new InvalidRefreshTokenException("Refresh token not found"));

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    // -------------------------------------------------------------------------
    // logout()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("logout calls revokeAllTokensForUser with the correct userId")
    void logout_authenticated_revokesAllUserTokens() {
        // Arrange
        Long userId = 42L;

        // Act
        authService.logout(userId);

        // Assert
        verify(refreshTokenService).revokeAllTokensForUser(userId);
    }
}
