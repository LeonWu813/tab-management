package com.tabvault.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core authentication service.
 *
 * Handles user registration, login credential validation, token issuance,
 * refresh token rotation, and logout.
 *
 * Passwords are BCrypt-hashed before storage and never logged or returned.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Registers a new user account.
     *
     * @param request registration details (email, password, displayName)
     * @return RegisterResponse containing displayName, accessToken, and refreshToken
     * @throws EmailAlreadyRegisteredException if the email is already in use (HTTP 409)
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            logger.info("Registration attempt for already-registered email");
            throw new EmailAlreadyRegisteredException("An account with this email address already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.email(), hashedPassword, request.displayName());
        userRepository.save(user);

        logger.info("New user registered userId={}", user.getId());

        String accessToken = jwtService.generateAccessToken(user.getId());
        String rawRefreshToken = refreshTokenService.createRefreshToken(user);

        return new RegisterResponse(user.getDisplayName(), accessToken, rawRefreshToken);
    }

    /**
     * Authenticates a user with email and password.
     *
     * Returns a generic error on failure — does not reveal whether the
     * account exists or the password is incorrect (AC-047).
     *
     * @param request login credentials
     * @return LoginResponse containing accessToken and refreshToken
     * @throws InvalidCredentialsException if credentials are invalid (HTTP 401)
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        // Always run password check to avoid timing attacks that could reveal account existence
        boolean credentialsValid = user != null
                && passwordEncoder.matches(request.password(), user.getPassword());

        if (!credentialsValid) {
            logger.debug("Login attempt with invalid credentials");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(user.getId());
        String rawRefreshToken = refreshTokenService.createRefreshToken(user);

        return new LoginResponse(accessToken, rawRefreshToken);
    }

    /**
     * Rotates a refresh token: revokes the submitted token and issues a new pair.
     *
     * @param rawRefreshToken the raw refresh token submitted by the client
     * @return RefreshResponse containing new accessToken and refreshToken
     * @throws InvalidRefreshTokenException if the token is invalid (HTTP 401)
     */
    @Transactional
    public RefreshResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(rawRefreshToken);
        User user = result.user();

        String accessToken = jwtService.generateAccessToken(user.getId());

        return new RefreshResponse(accessToken, result.newRawToken());
    }

    /**
     * Logs out a user by revoking all their active refresh tokens.
     *
     * @param userId the authenticated user's ID
     */
    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAllTokensForUser(userId);
        logger.info("User logged out userId={}", userId);
    }
}
