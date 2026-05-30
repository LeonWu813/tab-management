**Revision**: 1 | **Last Updated**: 2026-05-30

---

# TabVault — Product Requirements Document

---

## 1. Project Overview

TabVault is a Chrome Extension paired with a Progressive Web App (PWA) dashboard that helps users save, organize, and manage browser tabs they intend to revisit. Users accumulate open tabs across work, research, and personal contexts, which degrades system performance and causes content to be forgotten or missed. TabVault solves this by letting users close tabs immediately while preserving the content and its context.

The extension serves as the capture layer: one click (or keyboard shortcut) saves the current tab, and an AI analysis pipeline automatically generates a summary, suggests a category, and detects any time-sensitive deadlines embedded in the page content. The PWA dashboard serves as the management layer: users can browse, search, filter, annotate, and set reminders on saved items from any device. An auto-cleanup system surfaces staleness reminders and archives untouched items to keep the library actionable.

---

## 2. Goals & Non-Goals

### Goals

- Enable users to save any browser tab in one action (click or keyboard shortcut) and have the tab's content automatically summarized and categorized within 10 seconds of saving, so users can close the tab immediately with no manual effort.
- Provide a searchable dashboard that returns matching saved items within 3 seconds of a search query, so users can reliably locate any previously saved item without reopening their browser history.
- Surface time-sensitive content automatically: when a saved page contains a deadline, the system shall detect it and create a suggested reminder requiring no manual date entry by the user.
- Deliver a mobile-accessible dashboard so that users can review and manage saved items from a mobile browser or installed PWA without losing core functionality (browse, search, open original URL).
- Keep LLM token costs within the defined per-item budget (2,500–3,500 input tokens, 200–400 output tokens) by enforcing content truncation and URL-level deduplication before every API call.

### Non-Goals

- Native iOS and Android applications are out of scope for v1. Mobile access is provided via the responsive PWA only.
- Support for browsers other than Google Chrome is out of scope for v1. The extension targets Chrome Manifest V3 exclusively. Firefox, Safari, and Edge extensions are not planned for this release.
- Audio extraction and speech-to-text summarization for non-YouTube video platforms (Instagram Reels, TikTok) are out of scope for v1. These platforms receive metadata storage only.
- AI-powered semantic (embedding-based) search is out of scope for v1. Full-text search via PostgreSQL is the only search mechanism for this release.
- LLM-suggested smart groupings across saved items are out of scope for v1.
- A self-service admin panel for user management or system configuration is out of scope for v1.
- Internationalization and localization are out of scope for v1. The product is US English only.
- SSO, SAML, or OIDC federation is out of scope for v1. Authentication is email and password with JWT only.
- Multi-tenancy and organization-level account sharing are out of scope for v1. Each account is personal and single-user.
- A freemium billing or payment integration is out of scope for v1. Cost controls are implemented as server-side rate limits only.
- API versioning with backward compatibility guarantees across releases is out of scope for v1.

---

## 3. User Stories

### US-001: Save Current Tab

**As a** registered user,
**I want** to save the current browser tab in one action via the extension (click or keyboard shortcut),
**so that** I can close the tab immediately without losing the content or needing to remember to return to it.

**Acceptance Criteria**: AC-001, AC-002, AC-003, AC-004, AC-048, AC-049

---

### US-002: Save All Tabs in Window

**As a** registered user,
**I want** to save all open tabs in the current browser window at once,
**so that** I can close the entire window quickly when I need to free memory without manually saving each tab.

**Acceptance Criteria**: AC-005, AC-006, AC-050

---

### US-003: Automatic Summarization and Categorization

**As a** registered user,
**I want** saved links to be automatically summarized and assigned a suggested category,
**so that** I can understand what a saved item is about at a glance without reopening the original URL.

**Acceptance Criteria**: AC-007, AC-008, AC-009, AC-010

---

### US-004: Deadline Detection and Reminder Suggestion

**As a** registered user,
**I want** the system to detect time-sensitive dates in saved pages and suggest reminders,
**so that** I do not miss application deadlines, event registrations, or other time-sensitive actions embedded in content I have saved.

**Acceptance Criteria**: AC-011, AC-012, AC-013

---

### US-005: Browse and Search Saved Items

**As a** registered user,
**I want** to browse my saved items in the dashboard and search or filter them by title, summary, category, type, or tag,
**so that** I can quickly locate a specific saved item without scrolling through everything I have saved.

**Acceptance Criteria**: AC-014, AC-015, AC-016, AC-017

---

### US-006: Manage Categories

**As a** registered user,
**I want** to create, rename, reorder, and delete categories, and reassign items between categories,
**so that** I can organize my saved items according to my own classification scheme.

**Acceptance Criteria**: AC-018, AC-019, AC-020

---

### US-007: Set and Manage Reminders

**As a** registered user,
**I want** to manually create reminders on any saved item and manage (edit, snooze, dismiss) existing reminders,
**so that** I am notified at the right time to act on time-sensitive saved content.

**Acceptance Criteria**: AC-021, AC-022, AC-023, AC-024

---

### US-008: Create Plain Text Notes

**As a** registered user,
**I want** to create plain text notes from the extension popup or the dashboard,
**so that** I can capture context or ideas alongside saved links without switching to a separate note-taking app.

**Acceptance Criteria**: AC-025, AC-026, AC-027, AC-051

---

### US-009: YouTube Video Summarization

**As a** registered user,
**I want** YouTube video links I save to be automatically summarized using the video transcript,
**so that** I can understand the video content without watching it in full.

**Acceptance Criteria**: AC-028, AC-029, AC-030

---

### US-010: Other Video Platform Metadata Storage

**As a** registered user,
**I want** links to non-YouTube video platforms (Instagram Reels, TikTok) to be saved with basic metadata,
**so that** they appear in my dashboard with a title and thumbnail even when a full summary is unavailable.

**Acceptance Criteria**: AC-031, AC-032

---

### US-011: Auto-Cleanup Staleness Reminders

**As a** registered user,
**I want** to be reminded about saved items I have not visited in 30 days and to have them auto-archived if I do not act,
**so that** my saved library stays manageable and does not accumulate forgotten, irrelevant items.

**Acceptance Criteria**: AC-033, AC-034, AC-035, AC-036

---

### US-012: Pin Items and Configure Cleanup Settings

**As a** registered user,
**I want** to pin important items to exempt them from auto-cleanup and to configure or disable the staleness threshold,
**so that** I have full control over which items are protected and how aggressively the cleanup runs.

**Acceptance Criteria**: AC-037, AC-038, AC-039

---

### US-013: Offline Browsing of Saved Items

**As a** registered user,
**I want** the PWA dashboard to remain usable for browsing previously loaded items when I am offline,
**so that** I can review saved content even without an internet connection.

**Acceptance Criteria**: AC-040, AC-041

---

### US-014: Share URL to TabVault from Mobile

**As a** registered user on a mobile device,
**I want** to share a URL from any mobile app directly to TabVault via the system share sheet,
**so that** I can save content on mobile without needing to open a desktop browser.

**Acceptance Criteria**: AC-042, AC-043

---

### US-015: User Account Registration and Login

**As a** new user,
**I want** to register an account and log in from both the extension and the PWA dashboard,
**so that** my saved items are securely stored and accessible across devices under my account.

**Acceptance Criteria**: AC-044, AC-045, AC-046, AC-047

---

## 4. Tech Stack

| Component | Name + Version | Notes |
|---|---|---|
| Extension frontend | React 18 + TypeScript 5 + Vite 5 | Chrome Manifest V3 extension popup and service worker |
| PWA frontend | React 18 + TypeScript 5 + Vite 5 | Dashboard SPA, installable as PWA |
| Client state management | Zustand 4 | Client-side UI state in the PWA |
| Server state management | TanStack React Query 5 | Server data fetching and cache management in the PWA |
| CSS framework | Tailwind CSS 3 | Utility-first styling; no CSS modules or styled-components |
| Frontend form validation | Zod 3 | Client-side schema validation |
| Backend language | Java 21 | LTS release |
| Backend framework | Spring Boot 3.3 | Web, Security, Data JPA, Validation, WebClient modules |
| ORM | Spring Data JPA (Hibernate 6) + Flyway 10 | Repository pattern; database schema migrations via Flyway |
| Database | PostgreSQL 16 | Primary data store; full-text search via tsvector indexes |
| Cache | Redis 7.2 | URL deduplication cache, rate-limit counters, optional session store |
| Job scheduler | Spring @Scheduled + Quartz 2.3 | Quartz for persistent reminder and staleness-check jobs |
| API documentation | springdoc-openapi 2 (Swagger UI) | Auto-generates OpenAPI spec at /v3/api-docs |
| TypeScript type generation | openapi-typescript 6 | Generates frontend TypeScript interfaces from OpenAPI spec |
| Authentication | Spring Security 6 + jjwt 0.12 | JWT access tokens (15-min expiry) + refresh tokens (7-day expiry) |
| LLM | Claude API (claude-sonnet-4) via Spring WebClient | Summarization, categorization, deadline detection via tool-use pattern |
| Content extraction — articles | Jsoup 1.17 + Readability4J 0.8 | HTML parsing and article text extraction |
| Content extraction — YouTube | YouTube Data API v3 | Transcript retrieval for YouTube links |
| Content extraction — PDF | Apache PDFBox 3 | Text extraction from PDF links |
| Push notifications | webpush-java 1.2 | Web Push API for browser and PWA reminder notifications |
| Hosting — frontend | Vercel | Static site and PWA hosting |
| Hosting — backend + DB | Railway or Render | Backend service and managed PostgreSQL instance |

---

## 5. Architecture Overview

TabVault is organized into three layers: client, backend, and data.

**Client layer** comprises two frontend applications that share conventions but are deployed separately.

The Chrome Extension (Manifest V3) provides the capture UI via a popup and a background service worker. The service worker manages auth token storage and refresh, sends save requests to the backend REST API, and processes alarm events to display in-browser reminder notifications. The extension does not perform content extraction — it sends the URL and page title to the backend and receives the analysis result.

The PWA dashboard is a single-page React application that serves as the primary management interface for browsing, searching, editing, and organizing saved items. It is installable as a PWA on desktop and mobile. A service worker caches the app shell and previously loaded data for offline access. The Share Target API allows the installed PWA to receive URLs shared from other mobile apps.

**Backend layer** is a single Spring Boot 3 application exposing a REST API consumed by both clients. It is organized into four internal service areas:

- Auth Service: handles registration, login, JWT issuance, and token refresh.
- Item Service: handles CRUD for items, categories, and tags. Orchestrates the content analysis pipeline by calling Content Extraction and then the LLM Service when a new item is saved. Updates `last_visited_at` on item click-through.
- LLM Service: receives extracted text from the Item Service, checks the URL deduplication cache, calls the Claude API using the tool-use pattern, and returns structured analysis results (summary, category suggestion, detected deadlines) to the Item Service.
- Reminder Scheduler: evaluates upcoming reminders and staleness conditions on a daily schedule, creates staleness reminder records, and dispatches push notifications for due reminders via the Web Push API.

**Data layer**: PostgreSQL 16 is the primary store for all application data (users, items, categories, reminders, tags). Full-text search is served by PostgreSQL tsvector indexes on item titles, summaries, and note bodies. Redis 7.2 provides the URL deduplication cache, rate-limit counters, and optionally session storage; Redis is optional for MVP deployment and can be replaced by a PostgreSQL-backed cache table during initial development.

**Type synchronization**: Java DTOs annotated with springdoc-openapi are the source-of-truth API contract. The backend auto-generates an OpenAPI spec at `/v3/api-docs`. The `openapi-typescript` tool generates TypeScript interfaces from that spec for use by both frontend applications.

---

## 6. Module Breakdown

### MOD-001: Authentication

**Purpose**: Manages user registration, login, JWT access token issuance, refresh token rotation, and logout. Does not manage profile data or any item-level authorization.

**User Stories**: US-015

**Dependencies**: none

---

### MOD-002: Item Management

**Purpose**: Manages CRUD operations for saved items (links, notes, videos), category management, tag management, pin and archive state, and the `last_visited_at` timestamp update on item click-through. Orchestrates calls to MOD-004 (content extraction) and MOD-003 (content analysis) after a new item is saved. Does not perform extraction or LLM calls directly.

**User Stories**: US-001, US-002, US-005, US-006, US-008

**Dependencies**: MOD-001 (Authentication — user identity required for all item writes)

---

### MOD-003: Content Analysis Pipeline

**Purpose**: Receives extracted page text from MOD-002, calls the Claude API via the tool-use pattern to produce a summary, category suggestion, and deadline list, and returns structured results to MOD-002. Checks the URL deduplication cache before calling the API and writes results to the cache afterward. Invokes note categorization only when explicitly requested for a note longer than 50 words.

**User Stories**: US-003, US-004

**Dependencies**: MOD-001 (Authentication — provides the user's existing category list for prompt context), MOD-002 (Item Management — provides the item record and extracted text; receives analysis results)

---

### MOD-004: Content Extraction

**Purpose**: Extracts readable text or metadata from a URL so that MOD-003 can analyze it. Handles article pages (HTML parsing and content extraction), YouTube video transcripts (YouTube Data API v3), PDF links (PDFBox text extraction), and Open Graph metadata for non-YouTube video platform URLs. Returns extracted text or metadata to MOD-002.

**User Stories**: US-001, US-003, US-009, US-010

**Dependencies**: MOD-002 (Item Management — called as part of the item save flow)

---

### MOD-005: Reminder Service

**Purpose**: Manages reminder CRUD for both manual and auto-detected deadline reminders, evaluates upcoming reminders on a schedule, and dispatches push notifications to the user's registered push subscriptions when a reminder is due. Does not detect deadlines in content — that responsibility belongs to MOD-003.

**User Stories**: US-004, US-007

**Dependencies**: MOD-001 (Authentication — user identity for reminder ownership), MOD-002 (Item Management — item association for each reminder), MOD-003 (Content Analysis Pipeline — provides detected deadlines that seed suggested reminders)

---

### MOD-006: Auto-Cleanup Scheduler

**Purpose**: Runs daily scheduled jobs to identify non-pinned items not visited within the user's configured staleness threshold, creates staleness reminder records via MOD-005, and auto-archives items that pass the grace period without user interaction. Respects per-user opt-out and pin settings. Does not dispatch push notifications directly.

**User Stories**: US-011, US-012

**Dependencies**: MOD-002 (Item Management — reads item state; updates archive status), MOD-005 (Reminder Service — creates staleness reminder records)

---

### MOD-007: Chrome Extension

**Purpose**: Provides the browser capture UI (popup and background service worker) for saving tabs, saving all tabs, creating quick notes, viewing recent saves, and displaying in-browser reminder notifications. Manages auth token storage and refresh locally. Communicates with the backend REST API exclusively — does not access the database directly.

**User Stories**: US-001, US-002, US-007, US-008

**Dependencies**: MOD-001 (Authentication — login and token refresh), MOD-002 (Item Management — save and retrieve items), MOD-005 (Reminder Service — receive and dismiss notifications)

---

### MOD-008: PWA Dashboard

**Purpose**: Provides the management-layer web application where users browse, search, filter, edit, and organize saved items; manage categories; create and manage reminders; create plain text notes; and configure account settings including cleanup preferences. Implements PWA features: a service worker for offline caching of previously loaded data and the Share Target API for mobile URL sharing.

**User Stories**: US-005, US-006, US-007, US-008, US-011, US-012, US-013, US-014

**Dependencies**: MOD-001 (Authentication), MOD-002 (Item Management), MOD-005 (Reminder Service), MOD-006 (Auto-Cleanup Scheduler — user preference settings exposed via the settings UI)

---

## 7. Phases & Milestones

### Phase 1: Foundation

**Modules**: MOD-001, MOD-002, MOD-007, MOD-008

**Milestone**: A registered user can save the current tab and all tabs from the Chrome Extension, manually categorize and annotate saved items, and browse, search, and filter saved items in the PWA dashboard. All MOD-001, MOD-002, MOD-007, and MOD-008 Phase 1 acceptance criteria pass in the staging environment. No LLM integration is required at this phase.

---

### Phase 2: LLM Integration

**Modules**: MOD-003, MOD-004, MOD-005

**Milestone**: Saved links are automatically summarized and categorized by the Claude API within 10 seconds of saving. Detected deadlines generate suggested reminders. Manual reminders can be created. Push notifications are dispatched for due reminders. All MOD-003, MOD-004, and MOD-005 acceptance criteria pass in the staging environment.

---

### Phase 3: Video and Notes Enhancements

**Modules**: MOD-004 (YouTube transcript and non-YouTube metadata), MOD-002 (note auto-categorize), MOD-008 (note creation UI enhancements)

**Milestone**: YouTube video links are summarized from transcripts. Non-YouTube video links are saved with metadata and a "No summary available" label. Plain text notes can be created from both the extension and the dashboard. The note auto-categorize action invokes MOD-003 for notes longer than 50 words. All AC-028 through AC-032 and AC-025 through AC-027 and AC-051 pass in the staging environment.

---

### Phase 4: Polish and PWA

**Modules**: MOD-006, MOD-007 (keyboard shortcuts and batch save), MOD-008 (offline, share target, drag-and-drop, UX refinements)

**Milestone**: Auto-cleanup staleness reminders and auto-archiving are active and configurable per user. PWA service worker delivers offline browsing of previously loaded items. The Share Target API allows mobile URL sharing into TabVault. Extension keyboard shortcuts work for save, save-all, and quick note. The dashboard supports drag-and-drop category reassignment, grid/list toggle, and full-text search. All MOD-006 acceptance criteria and all Phase 4 PWA and UX acceptance criteria pass in the production environment with zero P0 bugs open.

---

## 8. Acceptance Criteria

### MOD-001 Acceptance Criteria

**AC-044**: The system shall create a new user account and return HTTP 201 with the user's display name and a JWT access token when a valid email address and a password of at least 8 characters are submitted to the registration endpoint.

**AC-045**: The system shall return HTTP 409 when a registration request is submitted with an email address already associated with an existing account.

**AC-046**: The system shall return a JWT access token valid for 15 minutes and a refresh token valid for 7 days in the response body when a user submits valid credentials to the login endpoint.

**AC-047**: The system shall return HTTP 401 with a generic error message that does not indicate whether the account exists or the password is incorrect when a user submits invalid credentials to the login endpoint.

---

### MOD-002 Acceptance Criteria

**AC-001**: The system shall create a new item record containing the URL, page title, favicon URL, and `created_at` timestamp when the extension submits a valid save request for the current tab.

**AC-002**: The system shall return the saved item record to the extension within 2 seconds of the save request being received, so the extension can display a confirmation toast without waiting for LLM analysis to complete.

**AC-003**: The system shall close the saved tab in the browser when the user has enabled the "close tab on save" option and the save request returns a success response.

**AC-004**: The system shall return HTTP 200 with the existing item record when a save request is received for a URL already saved by the same user and not yet deleted or archived, preventing duplicate items.

**AC-005**: The system shall accept a batch save request containing all URLs from the current browser window and enqueue each URL for sequential processing, returning HTTP 202 immediately to the extension.

**AC-006**: The system shall save all items from a batch request even when content extraction or LLM analysis fails for individual items; items with extraction or analysis failures shall be saved with the URL and page title only, with summary and category fields left blank.

**AC-018**: The system shall allow a user to create a category with a name of 1 to 50 characters, a hex color code, and an optional icon, and shall return the created category in the response.

**AC-019**: The system shall reassign all items in a deleted category to "uncategorized" rather than deleting the items when a category is deleted.

**AC-020**: The system shall update an item's category assignment and return the updated item record when the user submits a category reassignment request for an item they own.

**AC-025**: The system shall create a plain text note item with the user-provided body text when a note save request is submitted from the extension popup or the dashboard.

**AC-026**: The system shall store the note body as plain text without modifying, sanitizing, or interpreting the user's input.

**AC-027**: The system shall return note items in search results when the search query matches text in the note body.

---

### MOD-003 Acceptance Criteria

**AC-007**: The system shall send a Claude API request using the tool-use pattern with the `generate_summary` and `categorize_content` tools within 5 seconds of a new link item being created.

**AC-008**: The system shall truncate the extracted page text to a maximum of 3,000 tokens before including it in the Claude API request.

**AC-009**: The system shall skip the Claude API call and return the cached analysis result when the saved URL matches an entry in the URL deduplication cache.

**AC-010**: The system shall store the LLM-returned summary text, suggested category name, and content type on the item record and include them in the response to the client when analysis completes.

**AC-011**: The system shall invoke the `extract_deadlines` tool only when the LLM determines that time-sensitive content is present in the page text; the tool shall not be invoked on pages that contain no dates or deadlines.

**AC-012**: The system shall create a suggested reminder record with the detected date, label, and urgency level for each deadline returned by the `extract_deadlines` tool, with the reminder status set to "pending confirmation."

**AC-013**: The system shall not activate a reminder created from a detected deadline until the user has explicitly confirmed, modified, or accepted it; a pending-confirmation reminder shall not trigger push notifications.

---

### MOD-004 Acceptance Criteria

**AC-028**: The system shall detect a YouTube URL by regex pattern match and retrieve the video transcript using the YouTube Data API v3.

**AC-029**: The system shall pass the retrieved YouTube transcript to MOD-003 for summarization, applying the same 3,000-token truncation limit as article content, when the transcript is successfully retrieved.

**AC-030**: The system shall store the video title, YouTube oEmbed thumbnail URL, platform identifier "youtube", and LLM-generated summary on the item record when a YouTube link is saved and analyzed.

**AC-031**: The system shall detect non-YouTube video platform URLs (Instagram, TikTok) by URL pattern and store the URL, page title from Open Graph tags, thumbnail from the `og:image` tag, and platform name without calling the Claude API.

**AC-032**: The system shall display the label "No summary available — open to watch" for non-YouTube video items in the dashboard.

---

### MOD-005 Acceptance Criteria

**AC-021**: The system shall create a reminder for any item the user owns when a manual reminder request is submitted with a valid future due date and an optional label.

**AC-022**: The system shall dispatch a push notification to all registered push subscriptions for the user when a reminder's due time is reached, containing the item title and reminder label.

**AC-023**: The system shall allow a user to dismiss a reminder or update its due date and label when an update request is submitted for a reminder the user owns.

**AC-024**: The system shall display a badge indicator on the item card in the dashboard when the item has a reminder due within the next 24 hours.

---

### MOD-006 Acceptance Criteria

**AC-033**: The system shall create a staleness reminder with the label "You haven't revisited this in [N] days — still need it?" for each non-pinned, non-archived item whose `last_visited_at` (or `created_at` if never visited) is older than the user's configured staleness threshold (default: 30 days), evaluated once daily.

**AC-034**: The system shall auto-archive an item 7 days after its staleness reminder was dismissed without the user selecting "Keep" or visiting the item, provided the item has still not been visited during that 7-day period.

**AC-035**: The system shall clear any pending staleness reminder for an item and update `last_visited_at` to the current timestamp when the user opens the original URL from the item's dashboard entry or detail view.

**AC-036**: The system shall not update `last_visited_at` when the user scrolls past an item in the dashboard list view; only an explicit open of the original URL from the item's entry shall count as a visit.

**AC-037**: The system shall not create staleness reminders or auto-archive any item whose `is_pinned` flag is true, regardless of last visit time.

**AC-038**: The system shall apply an updated staleness threshold to the next daily scheduled job run when the user changes their staleness threshold setting to one of the allowed values: 14, 30, 60, or 90 days.

**AC-039**: The system shall not create any staleness reminders and shall not auto-archive any items for a user who has disabled auto-cleanup via the opt-out toggle in account settings.

---

### MOD-007 Acceptance Criteria

**AC-048**: The system shall display a list of the user's 5 most recently saved items in the extension popup, each showing the item title and relative saved timestamp.

**AC-049**: The system shall trigger the save-current-tab flow when the user activates the `Ctrl+Shift+S` shortcut on Windows or Linux, or `Cmd+Shift+S` on macOS.

**AC-050**: The system shall trigger the save-all-tabs flow for the current window when the user activates the `Ctrl+Shift+A` shortcut on Windows or Linux, or `Cmd+Shift+A` on macOS.

**AC-051**: The system shall open the quick note input in the extension popup when the user activates the `Ctrl+Shift+N` shortcut on Windows or Linux, or `Cmd+Shift+N` on macOS.

---

### MOD-008 Acceptance Criteria

**AC-014**: The system shall display only items matching the active filter criteria (category, content type, date range, or tag) when one or more filter controls are applied in the dashboard.

**AC-015**: The system shall return matching saved items within 3 seconds of a search query being submitted, searching across item titles, summaries, and note body text.

**AC-016**: The system shall allow a user to toggle the dashboard display between grid view and list view, and shall persist the selected view preference across sessions for that user.

**AC-017**: The system shall allow inline editing of an item's title, summary, and category assignment from the item card and save the change without requiring navigation to a separate detail page.

**AC-040**: The system shall serve previously loaded saved items and the app shell from the service worker cache and display them to the user when the PWA dashboard is opened without an internet connection.

**AC-041**: The system shall queue a note creation request in the service worker and submit it to the backend when connectivity is restored, when a user creates a note while offline.

**AC-042**: The system shall register as a Share Target so that users can share a URL to TabVault from any mobile app via the native share sheet on a device with the PWA installed.

**AC-043**: The system shall save the shared URL as a new item and trigger the content analysis pipeline when a URL is received via the Share Target.
