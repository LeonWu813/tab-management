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

### QA Run 1 — Functional Test — 2026-05-30

**QA Agent**: qa-mod-content-analysis
**QA Date**: 2026-05-30
**Workflow**: functional-test (first-time verification)
**Overall Result**: FAIL — 2 bugs found

#### Test Environment

- Docker: postgres:16 healthy, redis:7.2-alpine healthy
- Server: Spring Boot 3.3.5 on Java 25.0.1, port 8080
- Database: PostgreSQL 16.14 at localhost:5432
- Redis: 7.2 at localhost:6379
- Note: Server jar was rebuilt (mvn package -DskipTests) during QA because the pre-existing jar was stale and missing the V7 Flyway migration. The rebuild exposed V7 missing from the jar as a deployment issue.
- Note: CLAUDE_MODEL env var was temporarily overridden to `claude-sonnet-4-5` for end-to-end pipeline testing after BUG-2 was confirmed.

#### AC Verification Results

| AC | Description | Result | Notes |
|----|-------------|--------|-------|
| AC-007 | Claude API request within 5 seconds | PASS | Measured: 0.5s–4.8s pickup latency across 3 items |
| AC-008 | Page text truncated to max 3,000 tokens | PASS | MAX_INPUT_CHARS=12,000 (=3,000 tokens) enforced in truncateToTokenBudget() |
| AC-009 | Skip API call on cache hit | PASS | Server log confirms "Cache hit for URL analysis jobId=12 itemId=22"; Redis key confirmed present |
| AC-010 | Summary, category, content_type on item record and in response | PASS | DB: summary, suggested_category, content_type populated. GET /api/items/20 returns all three fields. |
| AC-011 | extract_deadlines invoked only when model detects time-sensitive content | PASS | Log shows deadlineCount=2 for deadline item, deadlineCount=0 for generic items |
| AC-012 | Suggested reminder created with detected date, label, urgency, status=pending_confirmation | FAIL | See BUG-1: PostgreSQL enum case mismatch — every INSERT fails with "invalid input value for enum reminder_status: PENDING_CONFIRMATION" |
| AC-013 | Pending-confirmation reminders do not trigger push notifications | PASS | No push notification code in contentanalysis module; status starts as PENDING_CONFIRMATION |
| AC-055 | content_analysis_jobs record created before save response | PASS | Job ID 10 created_at=03:21:28 UTC, item created_at=03:21:28 UTC — job exists at item creation time |
| AC-056 | Retry up to 3 times with retry_count and last_attempted_at updated | PASS | Confirmed: 8 permanently-failed jobs all have retry_count=3; last_attempted_at set on each attempt |
| AC-057 | At-least-once delivery via polling | PASS | findPendingAndRetryableJobs() query selects PENDING and FAILED-with-retry; job set COMPLETED only after item.save() |

---

#### BUG-1 (Implementation Bug) — AC-012 FAIL: PostgreSQL enum case mismatch for reminder_status and urgency_level

**Classification**: Implementation bug

**Severity**: Critical — AC-012 completely fails; no suggested reminders can ever be persisted

**Affected ACs**: AC-012

**Exact input**:
Item saved with deadline-rich title triggering extract_deadlines (item 23):
```
POST /api/items
{
  "url": "https://admissions.example.edu/apply?cycle=2026",
  "title": "Harvard Graduate Application - Deadline December 15 2026 - Early Deadline November 1 2026",
  "itemType": "LINK"
}
```

**Actual behavior**:
Server log at 2026-05-30T20:24:26:
```
ERROR: invalid input value for enum reminder_status: "PENDING_CONFIRMATION"
  at: insert into suggested_reminders (..., status, ...) values (?, ..., 'PENDING_CONFIRMATION', ...)
```
Both reminders fail to insert. Table `suggested_reminders` has 0 rows for item 23.

**Expected behavior per AC-012**:
One `suggested_reminders` row created for each detected deadline with `status = 'pending_confirmation'`, `detected_date`, `label`, `urgency` populated.

**Root cause**:
- `V6__create_suggested_reminders_table.sql` defines PostgreSQL enums with lowercase values: `reminder_status AS ENUM ('pending_confirmation', 'confirmed', 'dismissed')` and `urgency_level AS ENUM ('low', 'medium', 'high')`
- `ReminderStatus.java` declares uppercase enum constants: `PENDING_CONFIRMATION`, `CONFIRMED`, `DISMISSED`
- `UrgencyLevel.java` declares uppercase enum constants: `LOW`, `MEDIUM`, `HIGH`
- `SuggestedReminder.java` uses `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` which passes the Java enum `.name()` value (uppercase) directly to PostgreSQL, but PostgreSQL enum values are case-sensitive and lowercase-only
- `job_status` in V5 migration uses uppercase values (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`) matching Java's `JobStatus` enum — this inconsistency between V5 and V6 reveals V6 used a different casing convention

**Files to fix**:
- `backend/src/main/resources/db/migration/V6__create_suggested_reminders_table.sql` — change enum values to uppercase OR
- `backend/src/main/java/com/tabvault/backend/contentanalysis/ReminderStatus.java` and `UrgencyLevel.java` — change to lowercase constants OR add `@Column(columnDefinition = ...)` with a value converter

The simplest fix is to update V6 to use uppercase enum values (matching the job_status convention): `reminder_status AS ENUM ('PENDING_CONFIRMATION', 'CONFIRMED', 'DISMISSED')` and `urgency_level AS ENUM ('LOW', 'MEDIUM', 'HIGH')`. A new Flyway migration (V8) would be needed to drop and recreate the enums since the DB is already deployed.

---

#### BUG-2 (Implementation Bug) — Invalid Claude API model identifier causes all analysis jobs to fail

**Classification**: Implementation bug

**Severity**: Critical — with shipped configuration, every single analysis job fails permanently after 3 retries; pipeline is entirely non-functional out of the box

**Affected ACs**: AC-007, AC-008, AC-009, AC-010, AC-011, AC-012, AC-013 (all pipeline ACs depend on the API call succeeding)

**Exact input**:
Server starts with default `CLAUDE_MODEL=claude-sonnet-4-20250514` from `.env` and `application.properties`.

**Actual behavior**:
```
Claude API HTTP error status=404 NOT_FOUND body={"type":"error","error":{"type":"not_found_error","message":"model: claude-sonnet-4-20250514"},"request_id":"..."}
```
Confirmed directly: `POST https://api.anthropic.com/v1/messages` with `"model":"claude-sonnet-4-20250514"` returns HTTP 404.

**Expected behavior**:
Claude API should accept the model identifier and return analysis results.

**Root cause**:
The model identifier `claude-sonnet-4-20250514` does not exist in the Anthropic API. The API lists `claude-sonnet-4-5-20250929` as the current claude-sonnet-4 family model. The short alias `claude-sonnet-4-5` is also accepted.

**Files to fix**:
- `backend/.env` — change `CLAUDE_MODEL=claude-sonnet-4-20250514` to `CLAUDE_MODEL=claude-sonnet-4-5`
- `backend/src/main/resources/application.properties` — change default value `${app.content-analysis.model:claude-sonnet-4-20250514}` to `${app.content-analysis.model:claude-sonnet-4-5}`

**Note**: After overriding CLAUDE_MODEL to `claude-sonnet-4-5` for testing, the full pipeline works correctly: jobs complete in under 5 seconds, summaries are written to item records, cache deduplication works, and the extract_deadlines tool is properly invoked when deadline content is present.

---

#### ACs Verified as Passing (with corrected CLAUDE_MODEL)

All non-AC-012 acceptance criteria were verified against the live server with `CLAUDE_MODEL=claude-sonnet-4-5`:

- **AC-007**: Items 20, 21, 22 had pickup latencies of 2.8s, 0.5s, and 4.8s respectively — all under 5 seconds.
- **AC-008**: `ClaudeApiClient.truncateToTokenBudget()` enforces 12,000 char limit (3,000 tokens). Code-verified and unit-tested.
- **AC-009**: Server log line `Cache hit for URL analysis jobId=12 itemId=22 url=https://kubernetes.io/docs/home/` confirms API was skipped for duplicate URL. Redis key `analysis:url:https://kubernetes.io/docs/home/` confirmed present.
- **AC-010**: `GET /api/items/20` returns `{"summary":"...","suggestedCategory":"Programming","contentType":"documentation",...}`. DB confirmed same values in `items` table columns.
- **AC-011**: Server log `Analysis job completed jobId=13 itemId=23 ... deadlineCount=2` for deadline item; `deadlineCount=0` for generic items.
- **AC-013**: No push notification code exists in `backend/src/main/java/com/tabvault/backend/contentanalysis/`.
- **AC-055**: `content_analysis_jobs` row created at same timestamp as item (within milliseconds). Verified for items 20, 21, 22, 23.
- **AC-056**: 8 permanently-failed jobs all have `retry_count=3`. `last_attempted_at` updated on each attempt. `findPendingAndRetryableJobs` query excludes jobs with `retry_count >= maxRetries`.
- **AC-057**: JPQL query `WHERE j.status = 'PENDING' OR (j.status = 'FAILED' AND j.retryCount < :maxRetries)` — PROCESSING status correctly excluded so in-flight jobs are not double-processed. Job only set COMPLETED after `itemRepository.save(item)`.

#### Unit Test Results

All 107 unit tests pass (31 MOD-003 tests + 76 pre-existing tests). Unit tests use H2 in-memory database with VARCHAR-based enum columns and do not expose the PostgreSQL enum case mismatch.

---

## Bug-Fix Progress (2026-05-30)

### Fixes Applied

**BUG-1 — PostgreSQL enum case mismatch (AC-012 FAIL)**

Root cause: V6 DDL defined `reminder_status` and `urgency_level` enums with lowercase values; Java `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` passes `.name()` (uppercase) to PostgreSQL, which rejects it.

Fix: Created new Flyway migration `V8__fix_enum_case.sql` (immutable V6 not touched per Flyway convention).
Migration steps:
1. `DROP TABLE IF EXISTS suggested_reminders` — removes dependent rows
2. `DROP TYPE IF EXISTS reminder_status` and `urgency_level`
3. `CREATE TYPE reminder_status AS ENUM ('PENDING_CONFIRMATION', 'CONFIRMED', 'DISMISSED')` — uppercase matching Java
4. `CREATE TYPE urgency_level AS ENUM ('LOW', 'MEDIUM', 'HIGH')` — uppercase matching Java
5. `CREATE TABLE suggested_reminders` — identical DDL to V6 but with corrected enum types and uppercase DEFAULT values

Also created `backend/src/test/resources/db/migration/h2/V8__fix_enum_case.sql` — H2 no-op (H2 uses VARCHAR, not PostgreSQL enums; file required to keep Flyway schema history in sync across environments).

Files changed:
- `backend/src/main/resources/db/migration/V8__fix_enum_case.sql` — NEW
- `backend/src/test/resources/db/migration/h2/V8__fix_enum_case.sql` — NEW

**BUG-2 — Invalid Claude API model identifier**

Root cause: `application.properties` default `${CLAUDE_MODEL:claude-sonnet-4-20250514}` references a non-existent model ID; every job fails with HTTP 404 after 3 retries.

Fix: Updated default value in `application.properties` to `claude-sonnet-4-5` (accepted by Anthropic API).
`backend/.env` not modified (contains real secrets, not tracked in git; user updates manually if needed).

Files changed:
- `backend/src/main/resources/application.properties` — `claude-sonnet-4-20250514` → `claude-sonnet-4-5`

### Self-Check Results (Bug-Fix 2026-05-30)

**Automated checks:**
- Build (`mvn package -DskipTests`): PASS — BUILD SUCCESS in 1.643s
- Tests (`mvn test`): PASS — 107/107 tests pass, 0 failures, 0 errors (same test count as original implementation; H8 H2 no-op migration does not break any test)

**Judgment-based items:**
- V6 not modified: PASS — Flyway immutability convention respected; V8 is a new migration
- No hardcoded values introduced: PASS — model identifier remains config-driven via `${CLAUDE_MODEL:claude-sonnet-4-5}`
- H2 test migration created: PASS — V8 H2 file exists; Flyway schema history stays in sync between PostgreSQL and H2 environments
- No other module files touched: PASS — only V8 PostgreSQL migration, V8 H2 migration, and application.properties default value changed
- AC-012 fix is correct: PASS — PostgreSQL enum values now match Java `.name()` output; INSERT will bind 'PENDING_CONFIRMATION' against enum value 'PENDING_CONFIRMATION'

---

### QA Run 2 — Regression Test — 2026-05-30

**QA Agent**: qa-mod-content-analysis
**QA Date**: 2026-05-30
**Workflow**: regression-test (re-verification after BUG-1 and BUG-2 fixes)
**Overall Result**: FAIL — BUG-1 confirmed fixed; BUG-2 fix is incomplete (NEW REGRESSION BUG-2R)

#### Test Environment

- Docker: postgres:16 healthy, redis:7.2-alpine healthy
- Server: Spring Boot built from commit 72cb06a (`engineer-mod-content-analysis(bugfix): fix PostgreSQL enum case mismatch and invalid Claude model ID`), Java 25.0.1, port 8080
- Jar verified: `jar tf target/tabvault-backend-0.0.1-SNAPSHOT.jar | grep V8` confirms V8 migration is packaged
- Flyway history: V1–V8 all applied with `success=true`; V8 applied at 2026-05-31 03:39:37 UTC
- Unit tests: 107/107 pass (mvn test, exit 0)
- CLAUDE_MODEL override: server started with `CLAUDE_MODEL=claude-sonnet-4-5` (env var override required; see BUG-2R below)

#### Regression: BUG-1

**REGRESSION PASS AC-012**: Original failure scenario resolved.

Reproduction:
```
POST /api/items
{
  "url": "https://apply.university.edu/grad-admissions?cycle=fall2027",
  "title": "Graduate School Application - Priority Deadline January 15 2027 - Regular Deadline March 1 2027",
  "itemType": "LINK"
}
```

Result (item 25, job 15):
- Job 15 completed at 2026-05-31 03:41:52 UTC
- `suggested_reminders` table now contains 2 rows for item 25:

| id | item_id | detected_date | label | urgency | status |
|----|---------|---------------|-------|---------|--------|
| 1  | 25      | 2027-01-15    | Priority application deadline | HIGH | PENDING_CONFIRMATION |
| 2  | 25      | 2027-03-01    | Regular application deadline  | HIGH | PENDING_CONFIRMATION |

- Server log confirms: `Suggested reminder created itemId=25 userId=11 date=2027-01-15 label=Priority application deadline urgency=HIGH`
- PostgreSQL accepted uppercase enum values `PENDING_CONFIRMATION` and `HIGH` after V8 migration recreated enums with uppercase values

BUG-1 is fully resolved. V8 migration applied cleanly. No data corruption.

---

#### NEW REGRESSION BUG-2R (Implementation Bug) — BUG-2 fix is incomplete: .env.example still contains invalid model ID

**Classification**: Implementation bug — incomplete fix

**Severity**: Critical — any developer who follows the documented setup (cp .env.example .env) reproduces BUG-2 exactly; the application.properties default fix is overridden by the env var from .env

**Affected ACs**: AC-007, AC-008, AC-009, AC-010, AC-011, AC-012, AC-013 (all pipeline ACs depend on the API call succeeding — same scope as original BUG-2)

**Exact input**:
A developer follows `setup.md` Step 4 exactly:
```bash
cp .env.example .env
# (fills in real ANTHROPIC_API_KEY and other secrets)
# CLAUDE_MODEL=claude-sonnet-4-20250514 remains as-is from .env.example
./mvnw spring-boot:run
```

**Actual behavior**:
Spring Boot resolves `${CLAUDE_MODEL:claude-sonnet-4-5}` in application.properties. The `:claude-sonnet-4-5` default is only used when `CLAUDE_MODEL` is absent from the environment. Because `.env` sets `CLAUDE_MODEL=claude-sonnet-4-20250514`, the property resolves to `claude-sonnet-4-20250514`. Every Claude API call returns HTTP 404. All analysis jobs fail with retry_count=3 after 3 attempts.

Verified by inspecting the env variable resolution logic:
- `application.properties` line 46: `app.content-analysis.model=${CLAUDE_MODEL:claude-sonnet-4-5}`
- `.env` line 68: `CLAUDE_MODEL=claude-sonnet-4-20250514`
- `.env.example` line 68: `CLAUDE_MODEL=claude-sonnet-4-20250514`
- `setup.md` Step 4: instructs developer to `cp .env.example .env`

When the env var is present and set to the broken value, the fixed default in application.properties is never reached.

**QA session workaround**: This regression QA run was conducted with `export CLAUDE_MODEL=claude-sonnet-4-5` explicitly set in the shell before starting the server, overriding the broken .env value. All ACs other than this bug were re-verified using this workaround.

**Expected behavior**:
With a fresh environment following setup.md, `CLAUDE_MODEL` should default to a valid model ID (`claude-sonnet-4-5`) so the pipeline works without manual intervention.

**Files to fix**:
- `.env.example` — change `CLAUDE_MODEL=claude-sonnet-4-20250514` to `CLAUDE_MODEL=claude-sonnet-4-5`
- `.env` (tracked in .gitignore, not in git) — user must also update this manually, or the engineer should add a note to setup.md

Note: `.env` is correctly listed in `.gitignore` (verified: `grep '^\.env$' .gitignore` returns `.env`). Updating `.env.example` is the correct fix since it is the template developers copy.

---

#### Full AC Re-Verification (with CLAUDE_MODEL=claude-sonnet-4-5 override)

| AC | Description | Result | Notes |
|----|-------------|--------|-------|
| AC-007 | Claude API request within 5 seconds | PASS | Job 14: pickup=2.57s, Job 15: pickup=1.73s — both under 5s. Job created within 6ms of item creation. |
| AC-008 | Page text truncated to max 3,000 tokens | PASS | ClaudeApiClient.MAX_INPUT_CHARS=12_000 enforced in truncateToTokenBudget(). Code-verified. Unit-tested (10 tests). |
| AC-009 | Skip API call on cache hit | PASS | Duplicate URL POST for https://docs.spring.io/spring-framework/reference/ returned existing item 24 directly; Redis key present with TTL=604703s (~168 hours). No new job created for duplicate. |
| AC-010 | Summary, category, content_type on item record and in response | PASS | DB item 24: summary populated, suggested_category='Development', content_type='documentation'. GET /api/items/24 response includes all three fields. |
| AC-011 | extract_deadlines invoked only when model detects time-sensitive content | PASS | Log: job 14 deadlineCount=0 (generic Spring docs page), job 15 deadlineCount=2 (deadline-rich title). extract_deadlines switch case only executed when model returns that tool_use block. |
| AC-012 | Suggested reminder created with detected date, label, urgency, status=pending_confirmation | REGRESSION PASS | 2 rows in suggested_reminders for item 25. status=PENDING_CONFIRMATION, urgency=HIGH. V8 migration confirmed applied. BUG-1 fully resolved. |
| AC-013 | Pending-confirmation reminders do not trigger push notifications | PASS | No push/notification/dispatch code in backend/src/main/java/com/tabvault/backend/contentanalysis/. Status hardcoded to PENDING_CONFIRMATION. |
| AC-055 | content_analysis_jobs record created before save response | PASS | Job 14 created_at lags item 24 created_at by 0.006s. Job 15 lags item 25 by 0.002s. Jobs exist in DB before save response reaches client. |
| AC-056 | Retry up to 3 times with retry_count and last_attempted_at updated | PASS | 8 pre-existing FAILED jobs all have retry_count=3, last_attempted_at set. New jobs 14 and 15 completed on first attempt (retry_count=0). |
| AC-057 | At-least-once delivery via polling | PASS | JPQL query selects `status='PENDING' OR (status='FAILED' AND retryCount < maxRetries)`. No PENDING jobs remain unprocessed. Job status=COMPLETED only set after itemRepository.save(item). |

#### Unit Test Results (Regression Run)

- Command: `mvn test` (from backend directory)
- Result: 107 tests run, 0 failures, 0 errors, exit 0
- Test count identical to previous run — V8 H2 no-op migration introduced no new test failures
- All 31 MOD-003 tests pass; all 76 pre-existing tests pass

#### Summary

- BUG-1 (AC-012 enum case mismatch): CONFIRMED FIXED — V8 migration applied cleanly; suggested_reminders rows inserted successfully with PENDING_CONFIRMATION and urgency values.
- BUG-2 (invalid model ID in application.properties default): PARTIALLY FIXED — application.properties default corrected, but .env.example and .env still contain `CLAUDE_MODEL=claude-sonnet-4-20250514` which overrides the fixed default when sourced per setup.md instructions. Pipeline is still broken out of the box.
- NEW REGRESSION BUG-2R: `.env.example` must also be updated to `CLAUDE_MODEL=claude-sonnet-4-5` to complete the BUG-2 fix.
- All 10 ACs re-verified passing (with CLAUDE_MODEL override), confirming no other regressions were introduced by the V8 migration or application.properties change.

---

### QA Run 3 — Regression Test — 2026-05-30

**QA Agent**: qa-mod-content-analysis
**QA Date**: 2026-05-30
**Workflow**: regression-test (re-verification after BUG-2R fix: .env.example updated to claude-sonnet-4-5)
**Overall Result**: PASS — BUG-2R confirmed fixed; all 10 ACs verified passing

#### Test Environment

- Docker: postgres:16 healthy, redis:7.2-alpine healthy (Up 4+ hours, confirmed via `docker compose ps`)
- Server: Spring Boot 3.3.5 on Java 25.0.1, port 8080, built from commit 37c2b4b (`fix(config): correct CLAUDE_MODEL to claude-sonnet-4-5 in .env.example`)
- Jar rebuilt: `./mvnw package -DskipTests` — BUILD SUCCESS
- Flyway history: V1–V8 all validated (8 migrations), schema up to date, no migration necessary
- Unit tests: 107/107 pass (mvn test, exit 0)
- CLAUDE_MODEL: `.env.example` now contains `CLAUDE_MODEL=claude-sonnet-4-5` (fix confirmed). Server started loading `.env` (which still contains old value from prior to fix instruction) with shell override `CLAUDE_MODEL=claude-sonnet-4-5`. Pipeline called Anthropic API successfully, confirming `claude-sonnet-4-5` is accepted. Local `.env` (not tracked in git, per `.gitignore`) was not updated by the commit — this is expected; the user was instructed to update it manually.

#### Regression: BUG-2R

**REGRESSION PASS**: `.env.example` line 68 now reads `CLAUDE_MODEL=claude-sonnet-4-5`.

Verified:
```
grep -n "CLAUDE_MODEL" .env.example
68:CLAUDE_MODEL=claude-sonnet-4-5
```

The git-tracked template that all new developers copy via `cp .env.example .env` now contains the correct model identifier. Any developer following setup.md will get `CLAUDE_MODEL=claude-sonnet-4-5` in their environment. The broken identifier `claude-sonnet-4-20250514` is no longer present in `.env.example`.

End-to-end validation: server started with `CLAUDE_MODEL=claude-sonnet-4-5` processed job 16 (item 26) successfully — pipeline completed in 3.01s, real summary populated by Anthropic API. This confirms `claude-sonnet-4-5` is a valid model identifier accepted by the API.

BUG-2R is fully resolved.

#### Full AC Re-Verification (Round 3)

All 10 ACs re-verified against live server (items 26 and 27, jobs 16 and 17).

| AC | Description | Result | Evidence |
|----|-------------|--------|---------|
| AC-007 | Claude API request using tool-use pattern within 5 seconds | PASS | Job 16: item saved 20:54:47.948, picked up 20:54:50.962 = 3.01s. Job 17: item saved 20:55:23.564, picked up 20:55:24.332 = 0.77s. Both under 5s. |
| AC-008 | Page text truncated to max 3,000 tokens | PASS | `ClaudeApiClient.MAX_INPUT_CHARS = 12_000` (3,000 tokens × 4 chars/token per production.md convention). Code-verified. Unit-tested. |
| AC-009 | Skip API call and return cached result on URL match | PASS | POST of duplicate URL `https://docs.anthropic.com/claude/reference/messages-create` returned existing item 26 directly with populated summary/category/contentType. Log: `Duplicate save request for existing item userId=12 itemId=26`. No new job created (job count unchanged at 17). |
| AC-010 | Store summary, suggestedCategory, contentType on item record; include in response | PASS | `GET /api/items/26` returns `{"summary":"API reference documentation for Anthropic's Claude Messages endpoint...","suggestedCategory":"Development","contentType":"documentation"}`. DB columns confirmed populated. |
| AC-011 | extract_deadlines invoked only when model detects time-sensitive content | PASS | Job 16 log: `deadlineCount=0` (generic API docs). Job 17 log: `deadlineCount=2` (deadline-rich title). Tool only processed when model returns that tool_use block. |
| AC-012 | Suggested reminder created with detected date, label, urgency, status=pending_confirmation | PASS | DB query for item 27: 2 rows in suggested_reminders — id=3 detected_date=2026-10-01 label='Early Decision application deadline' urgency=HIGH status=PENDING_CONFIRMATION; id=4 detected_date=2027-01-05 label='Regular Decision application deadline' urgency=HIGH status=PENDING_CONFIRMATION. |
| AC-013 | Pending-confirmation reminders do not trigger push notifications | PASS | No push/notification/dispatch code in `backend/src/main/java/com/tabvault/backend/contentanalysis/`. References to "push notifications" in that directory are Javadoc/comment only. Status hardcoded to PENDING_CONFIRMATION at construction. |
| AC-055 | content_analysis_jobs record created before save response | PASS | Item 26 created_at=03:54:47.938099 UTC; job 16 created_at=03:54:47.946490 UTC — lag=8ms. Item 27 created_at=03:55:23.562137 UTC; job 17 created_at=03:55:23.563898 UTC — lag=2ms. Jobs exist before response returns to client. |
| AC-056 | Retry up to 3 times; retry_count and last_attempted_at updated on each attempt | PASS | DB: all 8 FAILED jobs have retry_count=3 (none exceed 3). New jobs 16 and 17 COMPLETED with retry_count=0. Query `GROUP BY status, retry_count` shows no row with retry_count > 3. |
| AC-057 | At-least-once delivery via polling; not COMPLETED until item updated | PASS | 0 PENDING jobs in content_analysis_jobs. JPQL query selects `status='PENDING' OR (status='FAILED' AND retryCount < maxRetries)`. Job status set COMPLETED only after `itemRepository.save(item)`. |

#### Checklist Items

- No HTML template comments in spec.md: PASS — `grep -c "<!--" spec.md` returns 0
- `.gitignore` lists `.env`: PASS — `grep '^\.env$' .gitignore` returns `.env`
- `.env.example` consistent with setup.md: PASS — `CLAUDE_MODEL=claude-sonnet-4-5` now correct
- No features implemented outside the spec: PASS — all contentanalysis module classes have a direct AC reference
- No spec requirements unimplemented: PASS — all 10 ACs verified with live observable behavior
- Automated QA script: PASS — `run-qa.sh` reports all checks passed (test command not configured in production.md, WARN only)

#### Unit Test Results (Round 3)

- Command: `./mvnw test` (Maven wrapper via discovered path)
- Result: 107 tests run, 0 failures, 0 errors, BUILD SUCCESS
- All 31 MOD-003 tests pass; all 76 pre-existing tests pass
- No test count change from previous runs

#### Summary

- BUG-2R (`.env.example` still had invalid model ID): CONFIRMED FIXED — `.env.example` line 68 is now `CLAUDE_MODEL=claude-sonnet-4-5` (commit 37c2b4b).
- All previously reported bugs (BUG-1, BUG-2, BUG-2R) are now resolved.
- All 10 ACs pass with real Anthropic API calls against live PostgreSQL and Redis.
- No new regressions introduced.
- MOD-003 Content Analysis Pipeline: PASS.
