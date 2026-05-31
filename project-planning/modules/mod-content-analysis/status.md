# Content Analysis Pipeline Status

## Engineering Progress

**Completed: 2026-05-30**

### Implementation Summary

Implemented the full MOD-003 Content Analysis Pipeline. All source files are in the feature-based directory `backend/src/main/java/com/tabvault/backend/contentanalysis/` per the Shared Conventions in production.md.

**Flyway migrations created:**
- `backend/src/main/resources/db/migration/V6__create_suggested_reminders_table.sql` — suggested_reminders table with reminder_status and urgency_level PostgreSQL enums
- `backend/src/main/resources/db/migration/V7__add_analysis_columns_to_items.sql` — adds suggested_category and content_type columns to items table (required by AC-010: store on item record)
- `backend/src/test/resources/db/migration/h2/V6__create_suggested_reminders_table.sql` — H2-compatible version (VARCHAR instead of ENUM)
- `backend/src/test/resources/db/migration/h2/V7__add_analysis_columns_to_items.sql` — H2-compatible ALTER TABLE

**Main source files created:**
- `AnalysisResult.java` — record holding summary, suggestedCategory, contentType, and detected deadlines
- `AnalysisCacheService.java` — Redis-backed URL deduplication cache (fail-open on Redis unavailability)
- `ClaudeApiClient.java` — Anthropic API HTTP client using WebClient + tool-use pattern; implements truncateToTokenBudget (3,000 tokens = 12,000 chars per production.md convention)
- `ClaudeApiException.java` — typed exception for API failures
- `ContentAnalysisJobPollingRepository.java` — JPA repository extension that adds findPendingAndRetryableJobs() query
- `ContentAnalysisService.java` — core pipeline: @Scheduled polling loop, processJob() with PROCESSING→COMPLETED/FAILED state machine, retry logic (MAX_RETRIES=3), reminder creation
- `ReminderStatus.java` — enum: PENDING_CONFIRMATION, CONFIRMED, DISMISSED
- `SchedulingConfig.java` — @EnableScheduling configuration
- `SuggestedReminder.java` — JPA entity for AI-detected deadline reminders; starts in PENDING_CONFIRMATION
- `SuggestedReminderRepository.java` — JPA repository
- `UrgencyLevel.java` — enum: LOW, MEDIUM, HIGH

**Cross-cutting changes (required by this module's spec):**
- `backend/pom.xml` — added spring-boot-starter-webflux (Spring WebClient is in production.md Tech Stack)
- `backend/src/main/resources/application.properties` — added ANTHROPIC_API_KEY, CLAUDE_MODEL, poll interval, cache TTL config (all from env vars)
- `backend/src/test/resources/application-test.properties` — added test values for new config keys; Redis URL for tests
- `backend/src/main/java/com/tabvault/backend/items/Item.java` — added suggestedCategory and contentType fields + getters/setters (required by AC-010: store on item record)
- `backend/src/main/java/com/tabvault/backend/items/ItemResponse.java` — added suggestedCategory and contentType to response DTO (required by AC-010: include in response to client)
- `backend/src/test/java/com/tabvault/backend/items/ItemControllerTest.java` — updated ItemResponse constructor calls to match new signature (compatibility fix; no test logic changed)

**Test files created:**
- `ContentAnalysisServiceTest.java` — 12 tests: polling, cache hit/miss, results written to item, reminder creation, retry logic, MAX_RETRIES permanent failure, item-not-found, category context
- `ClaudeApiClientTest.java` — 10 tests: truncation (below/at/above budget, null, blank), tool-use response parsing (summary+category, with deadlines, without deadlines), null response error
- `AnalysisCacheServiceTest.java` — 9 tests: cache hit, cache miss, null/blank URL, Redis failure fail-open (get and put), null result no-op, TTL write

**Endpoints / features implemented:**

All pipeline functionality is internal (no REST endpoints in this module — it is a background polling pipeline). Externally observable behavior: item records updated with summary, suggestedCategory, contentType after Claude API call; suggested_reminders rows created for detected deadlines.

**Claude API tool-use pattern:**
- `generate_summary` tool: always requested
- `categorize_content` tool: always requested
- `extract_deadlines` tool: defined in tool list but only invoked by the model when it detects time-sensitive content (AC-011)

### Self-Check Results (2026-05-30)

**Automated checks (self-check.sh):**
- Build: SKIP — no build command in production.md Build Config
- Lint: SKIP — no lint command in production.md Build Config
- Tests: SKIP — no test command in production.md Build Config
- Git scope: FLAGGED (known false positive pattern) — script flagged 6 files outside the module's planning directory:
  - `backend/pom.xml` — adds WebFlux for WebClient (Spring Boot WebClient module in production.md Tech Stack)
  - `backend/src/main/java/com/tabvault/backend/items/Item.java` — adds suggestedCategory/contentType (required by AC-010: store on item record)
  - `backend/src/main/java/com/tabvault/backend/items/ItemResponse.java` — exposes new fields (required by AC-010: include in client response)
  - `backend/src/main/resources/application.properties` — MOD-003 config from env vars (ANTHROPIC_API_KEY, CLAUDE_MODEL)
  - `backend/src/test/java/com/tabvault/backend/items/ItemControllerTest.java` — compatibility update (new ItemResponse constructor signature)
  - `backend/src/test/resources/application-test.properties` — test config for new env vars
  All are justified cross-cutting changes required by the spec. Same pattern as MOD-002.
- Manual build verification: PASS — `mvn compile` exits 0, no errors
- Manual test run: PASS — `mvn test` 107/107 tests pass (31 new tests), 0 failures, 0 errors

**Judgment-based items:**
- Every requirement in spec.md implemented: PASS
  - Claude API request using tool-use pattern within 5 seconds: PASS — @Scheduled fixedDelay=5000ms on processPendingJobs() picks up new PENDING jobs within 5s
  - Page text truncated to max 3,000 tokens: PASS — truncateToTokenBudget() in ClaudeApiClient: 12,000 chars = ~3,000 tokens per production.md convention
  - URL deduplication cache skip: PASS — AnalysisCacheService.get() checked before every API call; cache hit returns immediately
  - Summary, category, content type stored on item record and in client response: PASS — item.setSummary(), item.setSuggestedCategory(), item.setContentType(); ItemResponse includes all three
  - extract_deadlines only when LLM decides: PASS — tool is defined but only invoked if model includes it in tool_use blocks; parseToolUseResponse() processes it conditionally
  - Suggested reminder created with pending_confirmation status: PASS — SuggestedReminder constructor sets status=PENDING_CONFIRMATION
  - No push notifications for pending reminders: PASS — status=PENDING_CONFIRMATION; no notification dispatch in this module
  - content_analysis_jobs record written before save response: PASS — MOD-002 ItemService writes the job record; this module polls and processes
  - Retry up to 3 times with retry_count/last_attempted_at updates: PASS — handleJobFailure() increments retryCount, sets lastAttemptedAt, keeps status FAILED for retry eligibility; stops at MAX_RETRIES=3
  - At-least-once delivery via polling: PASS — @Scheduled polls for PENDING and retryable FAILED jobs; job not COMPLETED until item is updated
- Every acceptance criterion addressed with observable behavior: PASS
  - AC-007: @Scheduled fixedDelay=5000ms; job marked PROCESSING within one poll cycle of creation
  - AC-008: ClaudeApiClient.MAX_INPUT_CHARS=12_000 enforced in truncateToTokenBudget()
  - AC-009: analysisCacheService.get(url) checked before claudeApiClient.analyze(); verified by test (cache hit skips API call)
  - AC-010: item.setSummary(), setSuggestedCategory(), setContentType() written before COMPLETED; ItemResponse includes all three fields
  - AC-011: extract_deadlines block processed conditionally in parseToolUseResponse() switch case; not invoked unless model returns it
  - AC-012: createSuggestedReminders() creates one SuggestedReminder per deadline; verified by test (2 deadlines → 2 reminders)
  - AC-013: SuggestedReminder constructor hardcodes status=PENDING_CONFIRMATION; no notification code in this module
  - AC-055: MOD-002 creates the job record (already QA-verified); this module reads/processes it
  - AC-056: handleJobFailure() increments retryCount + sets lastAttemptedAt; FAILED after MAX_RETRIES=3; verified by tests
  - AC-057: findPendingAndRetryableJobs() returns both PENDING and FAILED-with-retry jobs; job status set COMPLETED only after itemRepository.save()
- Edge cases handled: PASS
  - Null/blank page text → truncateToTokenBudget returns ""; model receives "(No page content available)" message
  - Item deleted after job queued → item not found → job marked FAILED permanently (no retry)
  - Redis unavailability → fail-open, cache miss, API called normally
  - Claude API HTTP error → ClaudeApiException thrown → handleJobFailure increments retry count
  - Invalid deadline date string → try/catch in parseToolUseResponse logs warning, skips that deadline
  - Urgency string not recognized → defaults to MEDIUM
  - Model does not invoke required tools → summary defaults to title, category defaults to "General"
- No hardcoded configurable values: PASS
  - ANTHROPIC_API_KEY from ${app.content-analysis.anthropic-api-key}
  - CLAUDE_MODEL from ${app.content-analysis.model}
  - Poll interval from ${app.content-analysis.poll-interval-ms:5000}
  - Cache TTL from ${app.content-analysis.cache-ttl-hours:168}
  - All env vars defined in .env.example
- Code conventions followed: PASS — feature-based directory, PascalCase classes, camelCase vars, structured logging with key=value pairs, no silent catches (all catch blocks log and rethrow or handle meaningfully)
- No new dependencies outside tech stack: PASS — spring-boot-starter-webflux is in production.md Tech Stack ("Spring Boot 3.3 WebClient module")
- Code readability: PASS — each class has a single responsibility; Javadoc comments on all public methods; AC references in comments
- LLM API: PASS — model identifier read from ${app.content-analysis.model:claude-sonnet-4-20250514}, matching CLAUDE_MODEL env var in .env.example
- LLM prompt context budget: PASS — MAX_INPUT_CHARS=12,000 (3,000 tokens × 4 chars/token per production.md convention); truncation logs a WARN with original and truncated lengths; unit tests cover below-limit, at-limit, and over-limit cases

## QA Results
