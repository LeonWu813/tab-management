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

**QA Run Date:** 2026-05-30
**QA Workflow:** functional-test (first-time verification)
**QA Agent:** qa-mod-auth

### Infrastructure Checks

- PASS setup.md consistency: PostgreSQL port 5432 in setup.md matches docker-compose.yml host port 5432. Redis port 6379 in setup.md matches docker-compose.yml host port 6379. No port mismatches found.
- PASS .env.example completeness: All environment variables referenced in setup.md (DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, REDIS_URL, JWT_SECRET, JWT_ACCESS_TOKEN_EXPIRY_MINUTES, JWT_REFRESH_TOKEN_EXPIRY_DAYS, ANTHROPIC_API_KEY, VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT, YOUTUBE_API_KEY, CLAUDE_MODEL) are present in .env.example. ("DOWN" and "STATUS" grep matches are table cell values, not env var names.)
- PASS .gitignore: `.env` is listed in `.gitignore` (verified with `grep '^\.env$' .gitignore`).
- PASS .env file exists at project root with all Phase 1 required variables set (JWT_SECRET non-empty, DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, REDIS_URL, JWT_ACCESS_TOKEN_EXPIRY_MINUTES=15, JWT_REFRESH_TOKEN_EXPIRY_DAYS=7).
- PASS spec.md has no HTML template comments (`<!-- ... -->`).

### Automated Test Suite

No test command configured in `project-planning/production.md` Build Config (PM left blank). QA runner script (`run-qa.sh`) noted this and skipped automated run. Manual test invocation performed instead.

Manual run: `mvn test` (using system Maven 3.9.12)
Result: **33/33 tests pass, 0 failures, 0 errors** (exit 0)
Coverage: AuthControllerTest (9), AuthServiceTest (10), JwtServiceTest (6), RefreshTokenServiceTest (8)

### Live Server Verification

Server started with: `java -jar backend/target/tabvault-backend-0.0.1-SNAPSHOT.jar` (sourcing .env)
Server confirmed running: startup log shows `Started TabVaultApplication in 3.036 seconds`
Infrastructure: PostgreSQL 16 and Redis 7.2 both `Up (healthy)` per `docker compose ps`

---

#### AC-044: Register with valid credentials returns HTTP 201 with displayName and accessToken

**Input:**
```
POST /api/auth/register
{"email":"testuser1@example.com","password":"password123","displayName":"Test User 1"}
```
**Actual response (HTTP 201):**
```json
{"displayName":"Test User 1","accessToken":"eyJhbGciOiJIUzUxMiJ9..."}
```
**JWT payload decoded:** `{"sub":"1","iat":1780188149,"exp":1780189049}` — expiry delta = 900 seconds = **15.0 minutes** exactly.
**Result:** PASS AC-044

---

#### AC-045: Duplicate email returns HTTP 409

**Input:**
```
POST /api/auth/register
{"email":"testuser1@example.com","password":"password123","displayName":"Test User 1 Again"}
```
**Actual response (HTTP 409):**
```json
{"error":{"code":"EMAIL_ALREADY_REGISTERED","message":"An account with this email address already exists"}}
```
Error envelope matches shared convention `{ "error": { "code": "...", "message": "..." } }`.
**Result:** PASS AC-045

---

#### AC-046: Login with valid credentials returns accessToken (15-min) and refreshToken (7-day) in body

**Input:**
```
POST /api/auth/login
{"email":"testuser1@example.com","password":"password123"}
```
**Actual response (HTTP 200):**
```json
{"accessToken":"eyJhbGciOiJIUzUxMiJ9...","refreshToken":"kjg4A6HdKfZ0yc7ZaJcWj1UqrZqKaYmZZ8gu3gy2i7M"}
```
**Access token expiry verified:** JWT payload `{"sub":"1","iat":1780188175,"exp":1780189075}` — delta = 900 seconds = **15.0 minutes** exactly.
**Refresh token expiry verified:** Database query `SELECT (expires_at - created_at) FROM refresh_tokens` returned `6 days 23:59:59.999599` (effectively 7 days; sub-second delta is a timing artifact from microsecond precision, not a functional defect).
**Result:** PASS AC-046

---

#### AC-047: Invalid credentials return HTTP 401 with generic message (must not indicate whether account exists or password is incorrect)

**Test case A — wrong password for existing account:**
```
POST /api/auth/login
{"email":"testuser1@example.com","password":"wrongpassword"}
```
Actual response (HTTP 401): `{"error":{"code":"INVALID_CREDENTIALS","message":"Invalid email or password"}}`

**Test case B — non-existent email:**
```
POST /api/auth/login
{"email":"nonexistent@example.com","password":"password123"}
```
Actual response (HTTP 401): `{"error":{"code":"INVALID_CREDENTIALS","message":"Invalid email or password"}}`

Both cases return the identical message "Invalid email or password" — the response does not reveal whether the account exists or the password was wrong.
**Result:** PASS AC-047

---

### Edge Case Verification

- PASS boundary: Password exactly 8 characters (`"12345678"`) is accepted — returns HTTP 201.
- PASS validation: Password under 8 characters (`"short"`) returns HTTP 400 with `{"error":{"code":"VALIDATION_ERROR","message":"Password must be at least 8 characters","field":"password"}}`.
- PASS validation: Invalid email format (`"not-an-email"`) returns HTTP 400 with `{"error":{"code":"VALIDATION_ERROR","message":"Email must be a valid email address","field":"email"}}`.
- PASS validation: Empty request body returns HTTP 400 with a validation error.

### Shared Conventions Check (production.md)

- PASS error envelope: All error responses use `{ "error": { "code": "...", "message": "...", "field": "..." } }` envelope. `field` omitted via `@JsonInclude(NON_NULL)` when not applicable (correct per spec: "optional field name for validation errors").
- PASS feature-based directory: All auth files in `backend/src/main/java/com/tabvault/backend/auth/`. No top-level `/controllers`, `/services`, or `/repositories` directories present.
- PASS no hardcoded configurable values: JWT_SECRET, expiry minutes/days, CORS origins, DATABASE_URL all sourced from environment variables via `@Value`.
- PASS no SSO/OIDC/SAML (non-goal): Implementation is email + password + JWT only.
- PASS no multi-tenancy (non-goal): Each account is single-user.

### Spec Compliance: Gold-Plating Check

- `/refresh` endpoint (POST /api/auth/refresh): The spec Input/Output Contract explicitly lists "Token refresh endpoint: valid refresh token" as an input. This endpoint is in scope.
- `/logout` endpoint (POST /api/auth/logout): The module purpose states "Manages user registration, login, JWT access token issuance, refresh token rotation, and **logout**." This endpoint is in scope.
- `displayName` as registration input field: See SPEC ISSUE below.

### Spec Issue Found

[SPEC ISSUE: AC-044 Input/Output Contract gap — the registration endpoint Input section lists only "valid email address, password of at least 8 characters" but the Output section requires "user display name" to be returned. The implementation correctly requires `displayName` as a mandatory input field (since it must come from somewhere), but the spec's Input/Output Contract does not list it as an input parameter. This is a spec gap — the input contract is incomplete. When submitting `{"email":"...","password":"..."}` without `displayName`, the server returns HTTP 400: `{"error":{"code":"VALIDATION_ERROR","message":"Display name is required","field":"displayName"}}`. The spec does not define what happens when displayName is absent because it does not acknowledge displayName as an input at all. Escalate to PM to update the Input section of the registration endpoint to include `displayName` as a required string input — escalate to PM, not Engineer.]

### Overall Result

**SPEC ISSUE FOUND** — all 4 acceptance criteria (AC-044, AC-045, AC-046, AC-047) PASS against observable behavior. One spec-level gap found in the Input/Output Contract for AC-044: `displayName` is required as a registration input but is not listed in the spec's Input section.

Routing: escalate to PM to update the Input/Output Contract in spec.md.
