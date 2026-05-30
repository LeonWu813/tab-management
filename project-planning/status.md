# Project Status

## Last Action
<!-- Machine-readable block — handoff.sh parses this section -->
agent: pm
mode: change
module: n/a
result: success
commit: f9247e9a3ef428ba35f91c3d87a56022a31b865d
timestamp: 2026-05-30T00:00:00Z

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

2026-05-30 — [TRIVIAL] PRD v3: updated JavaScript runtime to Node.js 24.x LTS and npm 11.x. Node 20 reached EOL April 2026; Node 24 became active LTS October 2025. Added explicit Node.js and npm rows to prd.md Tech Stack table. Updated setup.md Runtime Versions table to match. Updated setup.md version mismatch note — Node/npm mismatch is now resolved; Java 25 note retained. No modules, dependencies, or phases affected.

2026-05-30 — [INIT] PRD v2 finalized. Incorporated all 6 Tech Lead PASS WITH CONDITIONS items and 4 key recommendations into prd.md. New ACs AC-052 through AC-066 added. Tech Lead conditions resolved. PRD ready for Doc-Sync handoff.

2026-05-30 — [INIT] PRD v1 finalized and approved by user. TabVault Chrome Extension + PWA dashboard with AI-powered tab saving, summarization, deadline detection, reminders, and auto-cleanup. 8 modules defined across 4 phases. Build/lint/test commands left blank to be filled later.

## Tech Lead Reviews

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
