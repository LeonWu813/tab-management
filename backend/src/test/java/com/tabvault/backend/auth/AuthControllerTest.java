package com.tabvault.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tabvault.backend.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests using standalone MockMvc — no Spring context, no database.
 * Verifies HTTP status codes, response shapes, and error envelope format.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/register
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-044: register returns HTTP 201 with displayName and accessToken on valid input")
    void register_validInput_returns201WithDisplayNameAndToken() throws Exception {
        RegisterResponse serviceResponse =
                new RegisterResponse("Alice", "access-token", "refresh-token");
        when(authService.register(any(RegisterRequest.class))).thenReturn(serviceResponse);

        String body = objectMapper.writeValueAsString(
                Map.of("email", "alice@example.com", "password", "password123", "displayName", "Alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("AC-045: register returns HTTP 409 when email is already registered")
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException("Email already in use"));

        String body = objectMapper.writeValueAsString(
                Map.of("email", "alice@example.com", "password", "password123", "displayName", "Alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("register returns HTTP 400 with error envelope when password is too short")
    void register_shortPassword_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "alice@example.com", "password", "short", "displayName", "Alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("register returns HTTP 400 when email is malformed")
    void register_malformedEmail_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "not-an-email", "password", "password123", "displayName", "Alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-046: login returns HTTP 200 with accessToken and refreshToken on valid credentials")
    void login_validCredentials_returns200WithTokens() throws Exception {
        LoginResponse serviceResponse = new LoginResponse("access-token", "refresh-token");
        when(authService.login(any(LoginRequest.class))).thenReturn(serviceResponse);

        String body = objectMapper.writeValueAsString(
                Map.of("email", "alice@example.com", "password", "password123"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("AC-047: login returns HTTP 401 with generic message on invalid credentials")
    void login_invalidCredentials_returns401WithGenericMessage() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        String body = objectMapper.writeValueAsString(
                Map.of("email", "alice@example.com", "password", "wrong-password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("login error message does not reveal whether account exists")
    void login_unknownEmail_sameErrorAsWrongPassword() throws Exception {
        // Both unknown email and wrong password must produce exactly the same message
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        String body = objectMapper.writeValueAsString(
                Map.of("email", "nobody@example.com", "password", "anything"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/refresh
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("refresh returns HTTP 200 with new accessToken and refreshToken on valid token")
    void refresh_validToken_returns200WithNewTokens() throws Exception {
        RefreshResponse serviceResponse = new RefreshResponse("new-access-token", "new-refresh-token");
        when(authService.refresh("valid-refresh")).thenReturn(serviceResponse);

        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "valid-refresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("refresh returns HTTP 401 when refresh token is invalid")
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refresh("bad-token"))
                .thenThrow(new InvalidRefreshTokenException("Refresh token not found"));

        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "bad-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }
}
