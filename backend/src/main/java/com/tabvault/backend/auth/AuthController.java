package com.tabvault.backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 *
 * All endpoints are public (no authentication required) except /logout,
 * which requires a valid JWT access token in the Authorization header.
 *
 * Endpoints:
 *   POST /api/auth/register  — register a new account
 *   POST /api/auth/login     — authenticate and receive tokens
 *   POST /api/auth/refresh   — rotate refresh token and get new access token
 *   POST /api/auth/logout    — revoke all refresh tokens for the authenticated user
 */
@Tag(name = "Authentication", description = "User registration, login, token refresh, and logout")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user account.
     *
     * AC-044: Returns HTTP 201 with displayName and accessToken on success.
     * AC-045: Returns HTTP 409 when the email is already registered.
     */
    @Operation(summary = "Register a new user account")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user with email and password.
     *
     * AC-046: Returns HTTP 200 with accessToken (15 min) and refreshToken (7 days).
     * AC-047: Returns HTTP 401 with a generic error on invalid credentials.
     */
    @Operation(summary = "Login with email and password")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Rotates a refresh token and issues a new access + refresh token pair.
     */
    @Operation(summary = "Refresh access token using a refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Revokes all refresh tokens for the authenticated user.
     * Requires a valid JWT access token in the Authorization: Bearer header.
     */
    @Operation(summary = "Logout — revoke all refresh tokens")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
