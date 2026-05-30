# Project Status

## Last Action
<!-- Machine-readable block — handoff.sh parses this section -->
agent: tech-lead
mode: review
module: n/a
result: success
commit: af4431ef23ca9b2530926a00b62b07e6f40c1922
timestamp: 2026-05-30T00:00:00Z

## Current Phase


## Phase Plan


## Build Config
<!-- Filled by PM during init. PM asks the user for the project's build, lint, and test
     commands and writes them here. Doc-Sync copies these to production.md Shared Conventions
     during the initial sync. Leave a value blank if that step doesn't apply. -->

Build:
Lint:
Test:

## PM Updates

2026-05-30 — [INIT] PRD v1 finalized and approved by user. TabVault Chrome Extension + PWA dashboard with AI-powered tab saving, summarization, deadline detection, reminders, and auto-cleanup. 8 modules defined across 4 phases. Build/lint/test commands left blank to be filled later.

## Tech Lead Reviews

### Review — 2026-05-30 — init

**Concerns** (must address before proceeding):

- **Missing refresh token storage spec for the Chrome Extension**: The PRD states that the extension service worker manages auth token storage and refresh (Section 5), but no AC specifies where tokens are stored (chrome.storage.local vs. chrome.storage.session), the refresh flow behavior when the service worker is inactive (MV3 service workers are ephemeral and can be killed between events), or how a lapsed token triggers re-authentication in the popup. MV3 service workers cannot hold in-memory token state reliably between invocations — this is the single most common implementation failure in Manifest V3 extensions. The AC set for MOD-001 and MOD-007 must either define storage strategy explicitly or the implementation team must treat this as a design decision that needs to be made before MOD-007 begins.

- **Async analysis pipeline coupling is underspecified**: AC-002 requires the saved item to be returned to the extension within 2 seconds, while AC-007 requires the Claude API call to be dispatched within 5 seconds of item creation. This implies a fire-and-forget async pipeline (save synchronously, analyze asynchronously), but no mechanism is specified — no message queue, no event table, no Spring ApplicationEvent. Without a durable async handoff between MOD-002 and MOD-003, a server restart between save and analysis permanently loses the analysis job. The PRD needs to either specify a lightweight job/event table in PostgreSQL or accept the loss-on-restart risk explicitly.

- **YouTube transcript availability is a hard dependency with no fallback defined**: AC-028 requires transcript retrieval via YouTube Data API v3, but YouTube transcripts are not available on all videos (auto-generated captions may be disabled; some videos have no captions). AC-029 only covers the success path ("when the transcript is successfully retrieved"). No AC covers the failure path: what is stored and displayed when the transcript is unavailable. Without a fallback spec, engineers will make inconsistent decisions across MOD-003 and MOD-004.

- **Push notification subscription lifecycle is unspecified**: MOD-005 dispatches push notifications via webpush-java, but no AC covers: how users register a push subscription (the browser Web Push permission flow), where VAPID keys are stored and generated, what happens when a push endpoint returns 410 Gone (expired subscription), or how multiple device subscriptions per user are managed. Without this, AC-022 is unimplementable in a production-safe way.

- **Share Target offline queue (AC-041 vs. AC-043) conflict**: AC-041 specifies that note creation requests are queued offline and submitted when connectivity resumes. AC-043 specifies that a URL received via Share Target triggers the content analysis pipeline immediately. If a shared URL arrives while the device is offline, neither AC covers this path. Share Target in PWA context requires the app to handle the incoming share even with poor connectivity — the PRD should explicitly declare whether shared URLs are queued like offline notes or blocked until online.

- **PostgreSQL full-text search index coverage is unstated**: AC-015 requires search across item titles, summaries, and note body text within 3 seconds. The PRD mentions tsvector indexes but gives no direction on index maintenance (trigger-based vs. Flyway-generated computed columns), language configuration (english vs. simple), or update strategy when summaries are written asynchronously after initial save. A missing or stale tsvector on a freshly saved item will silently break search for that item until the next update.

**Recommendations** (suggested improvements):

- **Define a content_analysis_jobs or outbox table**: A lightweight outbox pattern (a pending_jobs table with status, retry_count, last_attempted_at) is the minimal viable solution for reliable async analysis. Spring @Scheduled can poll the table every few seconds. This avoids introducing a message broker while still surviving restarts. If the team accepts at-most-once delivery, document it explicitly in status.md Decisions so future engineers know it is intentional.

- **Add a token_count estimation step before truncation in MOD-003**: The PRD specifies 3,000-token truncation but does not specify which tokenizer is used. Claude's tokenizer is not identical to word-count or character-count heuristics. Using a simple character-limit approximation (roughly 4 chars per token) is acceptable for v1, but the method must be codified in production.md so both MOD-003 and MOD-004 (YouTube transcript path) apply it identically.

- **Specify a VAPID key provisioning step in setup.md**: webpush-java requires a VAPID key pair. This must be generated once per environment, stored as environment variables, and rotated carefully. If it is omitted from setup, the Reminder Service will fail silently at runtime.

- **Rate-limit the batch save endpoint independently**: AC-005 allows saving all tabs in a window in one request. A user with 50+ tabs will trigger 50 LLM analysis jobs. The LLM rate limit mentioned in Goals (2,500–3,500 input tokens per item) implies per-item cost control, but no aggregate rate limit per user per hour is specified. Without one, a single bulk save can exhaust a user's quota and degrade analysis for all subsequent saves until the counter resets.

- **Clarify MOD-006 interaction with MOD-005 for duplicate staleness reminders**: AC-033 creates a staleness reminder via MOD-005 for each qualifying item, evaluated daily. If the daily job runs and a staleness reminder for an item already exists and has not been acted on, does the job create a second reminder, skip the item, or update the existing record? This is undefined and will lead to reminder duplication bugs.

- **Specify the Quartz job store configuration**: The PRD lists Quartz 2.3 for persistent reminder and staleness-check jobs. Quartz requires either a JDBC job store (tables in PostgreSQL) or an in-memory store. The in-memory store does not survive restarts (defeating the purpose of using Quartz over plain @Scheduled). The PRD should mandate the JDBC store, or explain why in-memory is acceptable given Railway/Render restart behavior.

- **Clarify Chrome Extension keyboard shortcut conflict potential**: AC-049 (`Ctrl+Shift+S`) and AC-050 (`Ctrl+Shift+A`) are commonly used by other extensions and system shortcuts. Chrome allows users to remap extension shortcuts, but the extension manifest must declare these, and documentation should note that conflicts are possible. This is low risk but worth noting.

- **Establish an explicit error envelope format for the REST API**: The PRD does not specify what error response bodies look like (field name, structure). Both MOD-007 and MOD-008 will parse error responses. If the format is not standardized, each frontend will handle errors differently and error states will be inconsistent.

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
- All modules shall follow feature-based directory structure: group files by module/feature, not by type (no top-level /controllers, /services, /repositories directories).

---

## Sync Reports


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
