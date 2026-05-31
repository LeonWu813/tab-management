# Project Status

## Last Action
<!-- Machine-readable block — handoff.sh parses this section -->
agent: qa-mod-reminder-service
mode: regression
module: mod-reminder-service
result: bugs-found
commit: 67529eade5ab4b04819d8039715fbf967beb87f1
timestamp: 2026-05-31T16:55:00Z

## Current Phase

Phase 1: Foundation

## Phase Plan

Phase 1: Foundation | Modules: MOD-001, MOD-002, MOD-007, MOD-008
Milestone: A registered user can save the current tab and all tabs from the Chrome Extension, manually categorize and annotate saved items, and browse, search, and filter saved items in the PWA dashboard. All MOD-001, MOD-002, MOD-007, and MOD-008 Phase 1 acceptance criteria pass in the staging environment. No LLM integration is required at this phase.

Phase 2: LLM Integration | Modules: MOD-003, MOD-004, MOD-005
Milestone: Saved links are automatically summarized and categorized by the Claude API within 10 seconds of saving. Detected deadlines generate suggested reminders. Manual reminders can be created. Push notifications are dispatched for due reminders. All MOD-003, MOD-004, and MOD-005 acceptance criteria pass in the staging environment.

Phase 3: Video and Notes Enhancements | Modules: MOD-004 (YouTube transcript and non-YouTube metadata), MOD-002 (note auto-categorize), MOD-008 (note creation UI enhancements)
Milestone: YouTube video links are summarized from transcripts. Non-YouTube video links are saved with metadata and a "No summary available" label. Plain text notes can be created from both the extension and the dashboard. The note auto-categorize action invokes MOD-003 for notes longer than 50 words. All AC-028 through AC-032 and AC-025 through AC-027 and AC-051 pass in the staging environment.

Phase 4: Polish and PWA | Modules: MOD-006, MOD-007 (keyboard shortcuts and batch save), MOD-008 (offline, share target, drag-and-drop, UX refinements)
Milestone: Auto-cleanup staleness reminders and auto-archiving are active and configurable per user. PWA service worker delivers offline browsing of previously loaded items. The Share Target API allows mobile URL sharing into TabVault. Extension keyboard shortcuts work for save, save-all, and quick note. The dashboard supports drag-and-drop category reassignment, grid/list toggle, and full-text search. All MOD-006 acceptance criteria and all Phase 4 PWA and UX acceptance criteria pass in the production environment with zero P0 bugs open.

## Build Config
<!-- Filled by PM during init. PM asks the user for the project's build, lint, and test
     commands and writes them here. Doc-Sync copies these to production.md Shared Conventions
     during the initial sync. Leave a value blank if that step doesn't apply. -->

Build:
Lint:
Test:

## PM Updates

2026-05-30 — [TRIVIAL] MOD-001 spec gap logged (QA escalation). QA verified all MOD-001 ACs pass (first-time verification). Post-verification, QA identified a spec omission: project-planning/modules/mod-auth/spec.md Input/Output Contract lists only "valid email address, password of at least 8 characters" as registration inputs, but the output requires a displayName field and the implementation correctly requires displayName as mandatory input. This is a spec clarification only — the PRD requirement (AC-044) and the implementation are both correct; the spec's Input section simply omitted the field. Required fix: add "displayName: required string" to the registration Input line in project-planning/modules/mod-auth/spec.md. No modules, dependencies, phases, or PRD text are affected. This entry is the handoff instruction to Doc-Sync.

2026-05-30 — [INIT] PRD v3 cleared for Doc-Sync handoff. Setup Confirmation gate satisfied: Tech Lead recorded infrastructure verification in status.md (postgres:16 and redis:7.2-alpine both healthy). PRD quality checklist passed — all 8 required sections present, no [DECISION NEEDED] markers, no orphan user stories, all ACs referenced and defined, all modules have explicit dependencies. No new Tech Lead findings require PRD edits: init-infra review concerns were environment/setup items (Java 25 advisory, VAPID key timing, Claude model ID note) already addressed in setup.md and .env.example; none are product requirements. PRD v3 is the authoritative version for Phase 1 development.

2026-05-30 — [TRIVIAL] PRD v3: updated JavaScript runtime to Node.js 24.x LTS and npm 11.x. Node 20 reached EOL April 2026; Node 24 became active LTS October 2025. Added explicit Node.js and npm rows to prd.md Tech Stack table. Updated setup.md Runtime Versions table to match. Updated setup.md version mismatch note — Node/npm mismatch is now resolved; Java 25 note retained. No modules, dependencies, or phases affected.

2026-05-30 — [INIT] PRD v2 finalized. Incorporated all 6 Tech Lead PASS WITH CONDITIONS items and 4 key recommendations into pmd.md. New ACs AC-052 through AC-066 added. Tech Lead conditions resolved. PRD ready for Doc-Sync handoff.

2026-05-30 — [INIT] PRD v1 finalized and approved by user. TabVault Chrome Extension + PWA dashboard with AI-powered tab saving, summarization, deadline detection, reminders, and auto-cleanup. 8 modules defined across 4 phases. Build/lint/test commands left blank to be filled later.

## Tech Lead Reviews

### Setup Confirmation — 2026-05-30

User confirmed: all `setup.md` steps complete. Docker Compose verified via `docker compose ps`:

| Service            | Image             | Status              | Ports                  |
|--------------------|-------------------|---------------------|------------------------|
| tabvault-postgres  | postgres:16       | Up (healthy)        | 0.0.0.0:5432->5432/tcp |
| tabvault-redis     | redis:7.2-alpine  | Up (healthy)        | 0.0.0.0:6379->6379/tcp |

Both required infrastructure services are healthy. Environment is ready for Phase 1 development.
PM agent may now tag [INIT].

---

### Review — 2026-05-30 — init-infra (version checks + infrastructure files)

**Context**: Re-invoked for init infrastructure review. `.env.example` and `docker-compose.yml` were never created; version checks were never run. This review covers those gaps and records any new architectural concerns surfaced by version mismatches.

**Version Check Results**:

| Tool | Required (PRD) | Installed | Status |
|------|----------------|-----------|--------|
| Java (JDK) | 21 LTS | 25.0.1 (OpenJDK) | MISMATCH — see concern below |
| Node.js | 20.x or 22.x LTS | v24.14.1 | MISMATCH — see concern below |
| npm | 10.x | 11.11.0 | MISMATCH — see concern below |
| Docker | 24.x or later | 29.4.0 | OK |
| Docker Compose | v2.x | v5.1.1 | ADVISORY — see note below |

**Concerns** (must address before proceeding):

- **Java 25 (non-LTS) vs. required Java 21 LTS**: Java 25 is a non-LTS preview release (released October 2025). Spring Boot 3.3's supported JDK baseline is Java 17 and Java 21. Java 25 introduces preview features and may expose Spring Boot 3.3 / Hibernate 6 to compatibility issues (e.g., changed reflection APIs, sealed class handling, bytecode changes). Risk is low for a greenfield project that does not use preview features, but it is not the tested configuration. Recommendation: install Java 21 LTS (e.g., `sdk install java 21-tem`) and set `JAVA_HOME` to the 21 installation. This avoids any Spring Boot 3.3 / Quartz 2.3 / Flyway 10 compatibility unknowns before the project has test coverage to catch regressions.

- **Node 24 (Current) vs. required 20.x or 22.x LTS**: Node 24 is the Current release channel (not yet LTS as of the knowledge cutoff). Vite 5, TanStack Query 5, and Zustand 4 are well-tested on Node 20/22 LTS. Node 24 may work, but peer-dependency resolution in npm and native bindings for some packages can behave differently on Current releases. Risk is low for this stack but is outside the stated spec. Recommendation: use `nvm install 22 && nvm use 22` to switch to Node 22 LTS for this project. If you choose to stay on Node 24, run `npm install` and note any warnings before proceeding.

- **npm 11 vs. required 10.x**: npm 11 ships with Node 24. If the team standardizes on Node 22 LTS (recommendation above), npm 10 will follow automatically via `nvm`. If you stay on Node 24 / npm 11, confirm that `openapi-typescript 6` and `web-push` (for VAPID key generation) install without error under npm 11.

**Recommendations** (advisory):

- **Docker Compose v5.1.1**: v5 is a significant version jump beyond the stated v2.x requirement. The `docker compose` (plugin) syntax is unchanged, so the `docker-compose.yml` created for this project will work. However, if your CI/CD environment or teammates use Docker Compose v2.x, ensure the compose file syntax is compatible (it is — no v3-only features are used). This is informational, not a blocker.

- **`.gitignore` check**: Before running `cp .env.example .env`, verify that `.env` (without the `.example` suffix) is in `.gitignore`. The `.env.example` file is committed intentionally (it contains no secrets). The `.env` file must never be committed.

- **VAPID key generation timing**: VAPID keys are only required for Phase 2+ (push notifications, AC-061). For Phase 1 setup, you may leave `VAPID_PUBLIC_KEY` and `VAPID_PRIVATE_KEY` as placeholders in `.env` — the backend startup gate for those keys (AC-061) only activates when the push notification module is wired. Update these before starting Phase 2.

- **Claude API model identifier**: The original project document uses `claude-sonnet-4-20250514` as the model string in the Java example (Section 11.4). The PRD tech stack table uses `claude-sonnet-4`. These may resolve to the same model, but the `.env.example` has been set to `claude-sonnet-4-20250514` (the versioned ID from the project document) to be consistent with the example code. Verify the current model ID at https://console.anthropic.com when wiring Phase 2.

**Infrastructure Files Created**:

- `.env.example` — all required environment variables with placeholder values and inline comments. Created at project root.
- `docker-compose.yml` — PostgreSQL 16 and Redis 7.2 services with named volumes, healthchecks, and exact versions from the PRD tech stack. DB is created automatically by the postgres image (no manual CREATE DATABASE step). Created at project root.

**Approved**:

- All prior architectural approvals from Review 2026-05-30 (init) remain in effect.
- `docker-compose.yml` service definitions are correct for the PRD tech stack (PostgreSQL 16, Redis 7.2).
- The Quartz JDBC job store, VAPID environment variable injection, and JWT secret injection are all represented as explicit variables in `.env.example`, consistent with Shared Conventions.
- Phase 1 can proceed without `ANTHROPIC_API_KEY`, `YOUTUBE_API_KEY`, or VAPID keys — all three are gated behind Phase 2/3 features. This preserves the Phase 1 critical path.

---

### Review — 2026-05-30 — init

**Concerns** (must address before proceeding):

- **Missing refresh token storage spec for the Chrome Extension**: RESOLVED — AC-052, AC-053, AC-054 added to MOD-007. chrome.storage.local mandated as exclusive token storage in Shared Conventions and MOD-007 Purpose. Refresh-on-wake and popup re-auth flow specified.

- **Async analysis pipeline coupling is underspecified**: RESOLVED — AC-055, AC-056, AC-057 added to MOD-003. `content_analysis_jobs` outbox table added to data model in Architecture Overview. Durable job creation, retry (up to 3), and at-least-once delivery guaranteed by polling specified. MOD-002 and MOD-003 Purpose sections updated.

- **YouTube transcript availability is a hard dependency with no fallback defined**: RESOLVED — AC-058, AC-059 added to MOD-004. Failure path: store video title and thumbnail, set summary field to null, display "Transcript unavailable — open to watch" label. MOD-004 Purpose updated.

- **Push notification subscription lifecycle is unspecified**: RESOLVED — AC-060, AC-061, AC-062, AC-063 added to MOD-005. Covers: client subscription registration, VAPID key env var config with startup failure gate, 410 Gone endpoint deletion, and multi-device dispatch. MOD-005 Purpose updated.

- **Share Target offline queue (AC-041 vs. AC-043) conflict**: RESOLVED — AC-064 added to MOD-008. Share Target URLs received offline are queued using the same service worker queue as AC-041 and submitted when connectivity resumes. MOD-008 Purpose and Architecture Overview updated.

- **PostgreSQL full-text search index coverage is unstated**: RESOLVED — Shared Convention added mandating trigger-based tsvector maintenance using `english` language config, applied on both initial item save and LLM summary write. Architecture Overview data layer updated.

**Recommendations** (incorporated):

- **content_analysis_jobs outbox table**: Added to data model and Shared Conventions.
- **REST error response envelope**: Shared Convention added specifying `{ "error": { "code", "message", "field" } }` envelope for all error responses.
- **Per-user hourly rate limit on batch-save**: AC-065 added to MOD-002 (100 tab URLs per rolling 60-minute window, HTTP 429 on violation). US-002 updated.
- **Quartz JDBC job store**: Shared Convention added mandating JDBC (PostgreSQL-backed) store in all environments.
- **Daily job idempotency**: AC-066 added to MOD-006. Job skips items that already have a pending or pending-confirmation staleness reminder. MOD-006 Purpose updated. US-011 updated.

**Approved**:

- Overall three-layer architecture (Extension capture layer, Spring Boot REST backend, PWA management layer) is well-suited to the requirements and clean in its separation.
- The decision to keep content extraction server-side (backend only, not in the extension) is correct — it avoids CORS issues, keeps the extension permissions minimal, and centralizes extraction logic.
- URL deduplication cache in Redis before every Claude API call is the right cost-control mechanism. The design is sound.
- JWT access + refresh token pattern (15-min / 7-day) is appropriate for this use case.
- Flyway for migrations is the right choice alongside Spring Data JPA — schema drift is a common failure mode and Flyway prevents it.
- The OpenAPI → openapi-typescript type generation pipeline eliminates a significant class of frontend/backend contract bugs and is a strong default for this stack.
- Phasing (Foundation → LLM → Video/Notes → Polish) is logical and delivers testable milestones. Phase 1 can ship without Claude API keys, which reduces the critical path.
- The decision to scope v1 to PostgreSQL full-text search (no embeddings) is prudent — it avoids pgvector complexity while still meeting the stated search requirement.
- Non-Goals are well-defined and realistic for v1. The boundary around SSO, multi-tenancy, and billing being out of scope is appropriate.
- MOD-003's tool-use pattern for the Claude API (generate_summary, categorize_content, extract_deadlines as conditional tools) is a sound design. Conditional invocation of extract_deadlines only when time-sensitive content is detected is a good token cost control.

**Proposed Shared Conventions** (for Doc-Sync to carry into production.md):

- All REST API error responses shall use a consistent JSON envelope: `{ "error": { "code": "ERROR_CODE", "message": "human-readable message", "field": "optional field name for validation errors" } }`.
- All async analysis jobs shall be tracked in a persistent job/outbox table (not in-memory) to survive service restarts.
- Token estimation for LLM input truncation shall use the approximation: characters / 4 = estimated tokens. Truncation shall occur at the character level before sending to the API.
- The tsvector columns for full-text search shall be maintained by PostgreSQL trigger functions (not application-layer updates) to ensure consistency regardless of the update path.
- VAPID key pairs shall be injected as environment variables (VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY) and never hardcoded or committed to source.
- Chrome Extension token storage shall use chrome.storage.local exclusively; tokens shall never be stored in memory only, given MV3 service worker lifecycle constraints.
- The Quartz job store shall be configured as JDBC (PostgreSQL-backed) in all environments, not in-memory.
- All modules shall follow feature-based directory structure: group files by feature/module, not by type (no top-level /controllers, /services, /repositories directories).

---

## Sync Reports

### Sync Report — Initial Sync — 2026-05-30
**Sync type:** initial
**PRD Revision:** 2
**Files created:**
- project-planning/production.md
- project-planning/modules/mod-auth/spec.md
- project-planning/modules/mod-auth/status.md
- project-planning/modules/mod-item-management/spec.md
- project-planning/modules/mod-item-management/status.md
- project-planning/modules/mod-content-analysis/spec.md
- project-planning/modules/mod-content-analysis/status.md
- project-planning/modules/mod-content-extraction/spec.md
- project-planning/modules/mod-content-extraction/status.md
- project-planning/modules/mod-reminder-service/spec.md
- project-planning/modules/mod-reminder-service/status.md
- project-planning/modules/mod-autocleanup/spec.md
- project-planning/modules/mod-autocleanup/status.md
- project-planning/modules/mod-chrome-extension/spec.md
- project-planning/modules/mod-chrome-extension/status.md
- project-planning/modules/mod-pwa-dashboard/spec.md
- project-planning/modules/mod-pwa-dashboard/status.md
- .claude/agents/engineer-mod-auth.md
- .claude/agents/qa-mod-auth.md
- .claude/agents/engineer-mod-item-management.md
- .claude/agents/qa-mod-item-management.md
- .claude/agents/engineer-mod-content-analysis.md
- .claude/agents/qa-mod-content-analysis.md
- .claude/agents/engineer-mod-content-extraction.md
- .claude/agents/qa-mod-content-extraction.md
- .claude/agents/engineer-mod-reminder-service.md
- .claude/agents/qa-mod-reminder-service.md
- .claude/agents/engineer-mod-autocleanup.md
- .claude/agents/qa-mod-autocleanup.md
- .claude/agents/engineer-mod-chrome-extension.md
- .claude/agents/qa-mod-chrome-extension.md
- .claude/agents/engineer-mod-pwa-dashboard.md
- .claude/agents/qa-mod-pwa-dashboard.md
**AMBIGUITY markers logged:**
- production.md Shared Conventions: Build config not yet provided — PM must fill in status.md Build Config. (Build/Lint/Test commands were left blank in status.md Build Config at the time of this sync.)
**CONFLICT markers logged:**
- none
**verify-sync.sh result:** PASS — 6/6 checks passed

### Sync Report — Trivial Sync — 2026-05-30
**Sync type:** trivial
**PRD Revision:** 3
**PM Update reference:** 2026-05-30 — [TRIVIAL] PRD v3: updated JavaScript runtime to Node.js 24.x LTS and npm 11.x
**Files modified:**
- project-planning/production.md — added "JavaScript runtime" (Node.js 24.x LTS) and "Package manager" (npm 11.x) rows to Tech Stack table; updated Last Synced from PRD Revision to 3
**Files created:** none
**Module removals noted:** none
**AMBIGUITY markers added:** none
**AMBIGUITY markers resolved:** none
**CONFLICT markers added:** none
**Note:** No module specs required update. Neither MOD-007 nor any other module spec contained Node.js or npm version strings — the wording change is fully contained in the Tech Stack table, which maps only to production.md. verify-sync.sh skipped per trivial passthrough rules.

### Sync Report — Trivial Sync — 2026-05-30
**Sync type:** trivial
**PM Update reference:** 2026-05-30 — [TRIVIAL] MOD-001 spec gap logged (QA escalation): add "displayName: required string" to registration Input line
**Files modified:**
- project-planning/modules/mod-auth/spec.md — Input/Output Contract, registration Input line: appended ", displayName: required string" to "valid email address, password of at least 8 characters"
**Files created:** none
**Module removals noted:** none
**AMBIGUITY markers added:** none
**AMBIGUITY markers resolved:** none
**CONFLICT markers added:** none
**Note:** Spec omission only. PRD (AC-044), implementation, and output contract were all already correct. No other module specs, production.md, or agent wrappers required update. verify-sync.sh skipped per trivial passthrough rules.

## Engineering Progress


## QA Results


## Decisions


## Module Map

| MOD-ID  | Directory              | Module Name               |
|---------|------------------------|---------------------------|
| MOD-001 | mod-auth               | Authentication            |
| MOD-002 | mod-item-management    | Item Management           |
| MOD-003 | mod-content-analysis   | Content Analysis Pipeline |
| MOD-004 | mod-content-extraction | Content Extraction        |
| MOD-005 | mod-reminder-service   | Reminder Service          |
| MOD-006 | mod-autocleanup        | Auto-Cleanup Scheduler    |
| MOD-007 | mod-chrome-extension   | Chrome Extension          |
| MOD-008 | mod-pwa-dashboard      | PWA Dashboard             |

## Checkpoint History


## Skill Recommendations

Pattern: Chrome Extension Manifest V3 service worker token storage and refresh — ephemeral service worker lifecycle requires chrome.storage.local persistence and explicit re-authentication triggers rather than in-memory token state.
Why: MV3 service worker token handling is a recurring failure point in Chrome Extension projects. A codified pattern covering storage strategy, refresh triggering, and popup re-auth flow would prevent this from being re-solved per project.
Agent: tech-lead

Pattern: Mockito subclass mock maker required for Java 21+ / Java 25 — ByteBuddy's inline mock maker cannot resolve Java 25's class file version; configure `mock-maker-subclass` via `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` and pin Mockito to 5.14+ in pom.xml.
Why: Spring Boot 3.3.x ships a Mockito version whose ByteBuddy dependency does not support Java 25. This silently causes all @Mock-annotated tests to fail at startup with "Unknown Java version: 0". The subclass mock maker avoids bytecode instrumentation entirely and works on all Java versions.
Agent: engineer-mod-auth

Pattern: PostgreSQL tsvector columns mapped in JPA entities must use columnDefinition = "tsvector" — without this, Hibernate schema validation (ddl-auto=validate) rejects the column type as "wrong column type" (expects varchar(255)) and prevents the application from starting.
Why: tsvector is a PostgreSQL-native type with no standard JDBC Types constant (it maps to Types#OTHER). Hibernate does not know how to match it to a Java String unless columnDefinition is explicitly set. This caused a complete server startup failure in MOD-002. The same issue applies to any other PostgreSQL-native type (e.g., PostgreSQL custom ENUMs) mapped to Java Strings without columnDefinition.
Agent: engineer-mod-item-management

Pattern: PostgreSQL custom ENUM types require more than columnDefinition in JPA — @Enumerated(EnumType.STRING) + columnDefinition = "my_enum" stops Hibernate schema validation from failing but does NOT fix JDBC parameter binding. At INSERT time, Hibernate still binds the value as character varying, and PostgreSQL rejects it (SQLState 42804). Fix: add @JdbcTypeCode(SqlTypes.NAMED_ENUM) to the field (Hibernate 6), or change the schema to use VARCHAR with a CHECK constraint.
Why: The BUG-002 fix in MOD-002 applied columnDefinition only, which masked the schema-validation error at startup but left the runtime INSERT error in place. All item-write endpoints returned HTTP 500 in regression testing. This pattern is likely to recur on any entity using a PostgreSQL custom ENUM.
Agent: engineer-mod-item-management

Pattern: Multiple @RestControllerAdvice beans require explicit @Order — when GlobalExceptionHandler uses @ExceptionHandler(Exception.class) and a module-specific handler (e.g., ItemExceptionHandler) defines specific exception handlers, Spring may route to the catch-all first if no @Order is set. Fix: annotate the specific handler with @Order(Ordered.HIGHEST_PRECEDENCE) and the catch-all with @Order(Ordered.LOWEST_PRECEDENCE).
Why: BUG-003 in MOD-002 caused ItemNotFoundException, CategoryNotFoundException, and BatchRateLimitExceededException to all return HTTP 500 instead of 404/404/429. The specific handlers in ItemExceptionHandler were correctly written but never fired. This is a non-obvious Spring MVC behavior that will affect any project with multiple @RestControllerAdvice beans.
Agent: engineer-mod-item-management

Pattern: PostgreSQL ENUM case must match Java enum .name() exactly when using @JdbcTypeCode(SqlTypes.NAMED_ENUM) — Hibernate 6's NAMED_ENUM type passes the Java enum constant's .name() value directly to PostgreSQL without transformation. If the SQL DDL defines enum labels as lowercase (e.g., 'pending_confirmation') but the Java enum uses uppercase (PENDING_CONFIRMATION), every INSERT fails with "invalid input value for enum". Fix: make SQL DDL enum values uppercase to match Java convention (consistent with job_status pattern), or add a custom JdbcType to lowercase before binding.
Why: BUG-1 in MOD-003 caused every suggested_reminders INSERT to fail. The job_status enum in V5 used uppercase (matching Java), but the reminder_status and urgency_level enums in V6 used lowercase (not matching Java). This inconsistency is invisible in unit tests (H2 uses VARCHAR for enums) and only surfaces against real PostgreSQL.
Agent: engineer-mod-content-analysis

Pattern: Spring Boot ${ENV_VAR:default} fallback is defeated when the env var is set to a wrong value — the colon-default syntax in application.properties only applies when the environment variable is absent. If the variable is present but wrong (e.g., CLAUDE_MODEL=claude-sonnet-4-20250514 from .env.example), the wrong value is used and the default is never reached. Fix: update .env.example (the developer-facing template) to a valid default value, not just application.properties.
Why: BUG-2 in MOD-003 was only partially fixed by updating the application.properties default. The .env.example template still contained the invalid model ID. Any developer following setup.md (cp .env.example .env) reproduced the bug exactly because sourcing .env set CLAUDE_MODEL to the broken value, defeating the fixed default. This pattern applies to any env-var-driven config where the template file has a placeholder that is silently wrong.
Agent: engineer-mod-content-analysis

Pattern: Spring WebClient uri(String) double-encodes already-percent-encoded URLs — when a fully-formed URL string containing percent-encoded query parameter values (e.g., from URLEncoder.encode()) is passed to webClient.get().uri(string), Spring's UriComponentsBuilder re-encodes the % characters, producing double-encoded URLs. The remote server receives malformed query parameters and returns 404. Fix: use webClient.get().uri(java.net.URI.create(encodedUrlString)) instead, which passes the URI as-is without re-encoding.
Why: BUG-1 in MOD-004 caused every YouTube oEmbed fetch to return 404, leaving thumbnail_url and video title null for all YouTube items. The bug was silent: unit tests mock WebClient and never make real HTTP calls, so all 140 tests passed while the live server consistently failed the oEmbed call. Any Spring WebClient usage that pre-encodes a URL with URLEncoder before passing it to uri(String) will hit this issue.
Agent: engineer-mod-content-extraction

Pattern: Quartz 2.3 CronExpression rejects `*` in both day-of-month and day-of-week — Quartz's own cron parser throws `ParseException: Support for specifying both a day-of-week AND a day-of-month parameter is not implemented` when both fields are `*`. Spring's @Scheduled cron parser accepts it without error. Fix: use `?` for day-of-week when day-of-month is `*` (e.g. `0 0 8 * * ?` not `0 0 8 * * *`). Apply the fix in application.properties, .env.example, application-test.properties, and any @Value or @Scheduled fallback default strings. Unit tests with in-memory Quartz store and @WebMvcTest context do NOT exercise CronScheduleBuilder and will not catch this — only live server startup reveals it.
Why: The Quartz JDBC fix for MOD-005 introduced a startup-blocking BeanInstantiationException. The default cron `0 0 8 * * *` was valid for Spring @Scheduled but invalid for Quartz CronScheduleBuilder. The entire server failed to start, blocking all 8 ACs. The fix is one character per occurrence (`*` to `?`), but every config location must be updated including .env.example (the developer template), or the bug will recur for any developer following setup.md.
Agent: engineer-mod-reminder-service

Pattern: Spring Boot Quartz JDBC auto-configuration is bypassed when a named quartzDS datasource is declared — when `spring.quartz.properties.org.quartz.jobStore.dataSource=quartzDS` is set in application.properties, Quartz's StdSchedulerFactory takes over datasource management and requires `driver`, `URL`, `user`, and `password` for that named datasource. Spring Boot's QuartzAutoConfiguration (which would otherwise share the application's primary DataSource automatically) is bypassed. If the quartzDS block is incomplete, Quartz throws `SchedulerException: Driver not specified for DataSource: quartzDS` and the server cannot start. Fix: remove the quartzDS datasource properties entirely and let Spring Boot's QuartzAutoConfiguration share the application's HikariCP DataSource automatically (the standard pattern for spring.quartz.job-store-type=jdbc).
Why: QA Run 3 for MOD-005 found the server still cannot start after the cron fix. The quartzDS block was added during the Quartz JDBC implementation but is incomplete — only provider=hikaricp was specified, without driver/URL/user/password. This is the third consecutive startup failure in the same Quartz configuration block, each one hiding the next. Unit tests with in-memory Quartz do not exercise this code path.
Agent: engineer-mod-reminder-service
