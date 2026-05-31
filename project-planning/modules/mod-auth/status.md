# Authentication Status

## Engineering Progress

**Completed: 2026-05-30**

### Implementation Summary

Created Spring Boot backend at `backend/` with the full mod-auth feature. Feature-based directory structure: all auth source files live in `backend/src/main/java/com/tabvault/backend/auth/` and shared error utilities in `backend/src/main/java/com/tabvault/backend/shared/error/`.

**Files created:**
- `backend/pom.xml` — Spring Boot 3.3.5 project with all tech-stack dependencies (Spring Security 6, jjwt 0.12.6, Flyway 10, springdoc-openapi 2, Spring Data JPA, PostgreSQL, H2 for tests, Mockito 5.17.0 override for Java 25 compat)
- `backend/src/main/resources/application.properties` — all config from env vars (JWT_SECRET, JWT_ACCESS_TOKEN_EXPIRY_MINUTES, JWT_REFRESH_TOKEN_EXPIRY_DAYS, DATABASE_URL, CORS_ALLOWED_ORIGINS)
- `backend/src/main/resources/db/migration/V1__create_users_table.sql` — users table with unique email index
- `backend/src/main/resources/db/migration/V2__create_refresh_tokens_table.sql` — refresh_tokens table with FK to users, token_hash unique constraint
- `backend/src/main/java/com/tabvault/backend/TabVaultApplication.java` — Spring Boot entry point
- `backend/src/main/java/com/tabvault/backend/auth/User.java` — JPA entity
- `backend/src/main/java/com/tabvault/backend/auth/RefreshToken.java` — JPA entity, stores SHA-256 hash of raw token
- `backend/src/main/java/com/tabvault/backend/auth/UserRepository.java` — JPA repository
- `backend/src/main/java/com/tabvault/backend/auth/RefreshTokenRepository.java` — JPA repository
- `backend/src/main/java/com/tabvault/backend/auth/JwtService.java` — JWT generation and validation (jjwt 0.12)
- `backend/src/main/java/com/tabvault/backend/auth/RefreshTokenService.java` — opaque refresh token lifecycle with rotation
- `backend/src/main/java/com/tabvault/backend/auth/AuthService.java` — register, login, refresh, logout business logic
- `backend/src/main/java/com/tabvault/backend/auth/AuthController.java` — REST endpoints: POST /api/auth/register, /login, /refresh, /logout
- `backend/src/main/java/com/tabvault/backend/auth/AuthExceptionHandler.java` — maps domain exceptions to HTTP responses
- `backend/src/main/java/com/tabvault/backend/auth/SecurityConfig.java` — stateless JWT security, BCrypt password encoder, CORS
- `backend/src/main/java/com/tabvault/backend/auth/JwtAuthenticationFilter.java` — servlet filter setting security context from Bearer token
- `backend/src/main/java/com/tabvault/backend/auth/RegisterRequest.java` / `LoginRequest.java` / `RefreshRequest.java` — validated request DTOs
- `backend/src/main/java/com/tabvault/backend/auth/RegisterResponse.java` / `LoginResponse.java` / `RefreshResponse.java` — response DTOs
- `backend/src/main/java/com/tabvault/backend/auth/InvalidCredentialsException.java` / `EmailAlreadyRegisteredException.java` / `InvalidRefreshTokenException.java` — typed domain exceptions
- `backend/src/main/java/com/tabvault/backend/shared/error/ApiError.java` / `ApiErrorResponse.java` / `GlobalExceptionHandler.java` — standard error envelope
- `backend/src/test/java/com/tabvault/backend/auth/AuthServiceTest.java` — 10 unit tests (mocked deps)
- `backend/src/test/java/com/tabvault/backend/auth/JwtServiceTest.java` — 6 unit tests (no mocks)
- `backend/src/test/java/com/tabvault/backend/auth/RefreshTokenServiceTest.java` — 8 unit tests (mocked repo)
- `backend/src/test/java/com/tabvault/backend/auth/AuthControllerTest.java` — 9 MockMvc tests (AC coverage)
- `backend/src/test/resources/application-test.properties` — H2 in-memory test config
- `backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — subclass mock maker for Java 25 compat
- `backend/.mvn/wrapper/maven-wrapper.jar` and `maven-wrapper.properties` — Maven wrapper for self-contained builds

### Self-Check Results (2026-05-30)

**Automated checks (self-check.sh):**
- Build: SKIP — no build command in production.md Build Config (PM has not set it yet)
- Lint: SKIP — no lint command in production.md Build Config
- Tests: SKIP — no test command in production.md Build Config
- Git scope: SKIP — uncommitted changes (pre-commit state; files staged correctly)
- Manual build verification: PASS — `mvn compile` exits 0, no errors
- Manual test run: PASS — `mvn test` 33/33 tests pass, 0 failures, 0 errors

**Judgment-based items:**
- Every requirement in spec.md implemented: PASS — all 4 requirements and AC-044 through AC-047 addressed
- Every acceptance criterion addressed with observable behavior: PASS
  - AC-044: POST /api/auth/register returns HTTP 201 with displayName + accessToken
  - AC-045: Duplicate email returns HTTP 409 with { "error": { "code": "EMAIL_ALREADY_REGISTERED" } }
  - AC-046: POST /api/auth/login returns accessToken (15-min) + refreshToken (7-day) in body
  - AC-047: Invalid credentials return HTTP 401 with generic "Invalid email or password" message (same for unknown email and wrong password)
- Edge cases handled: PASS — empty inputs rejected by @Valid, password < 8 chars rejected, malformed email rejected, expired/revoked refresh tokens detected
- No hardcoded configurable values: PASS — JWT_SECRET, expiry, CORS origins, DB URL all from @Value env vars
- Code conventions followed: PASS — feature-based directory, PascalCase classes, camelCase vars, descriptive names, no silent catches
- No new dependencies outside tech stack: PASS — all deps are in production.md Tech Stack
- Code readability: PASS — each class has a single responsibility with clear Javadoc
- AI/LLM API calls: N/A — this module makes no LLM calls
- LLM prompt construction: N/A — not applicable to auth module

**Note on Java 25 / Mockito compatibility:**
The environment runs Java 25 (non-LTS). Mockito's inline mock maker cannot instrument classes on Java 25 due to a ByteBuddy version resolution issue ("Unknown Java version: 0"). Resolved by pinning Mockito to 5.17.0 (overrides Spring Boot 3.3.5's default) and configuring the subclass mock maker via `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`. This is a known Java 25 compatibility constraint, not a production concern.

## QA Results
