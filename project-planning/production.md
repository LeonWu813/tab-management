**Last Synced from PRD Revision**: 3 | **Last Updated**: 2026-05-30

---

## Project Summary

TabVault is a Chrome Extension paired with a Progressive Web App (PWA) dashboard that helps users save, organize, and manage browser tabs they intend to revisit. Users accumulate open tabs across work, research, and personal contexts, which degrades system performance and causes content to be forgotten or missed. TabVault solves this by letting users close tabs immediately while preserving the content and its context.

The extension serves as the capture layer: one click (or keyboard shortcut) saves the current tab, and an AI analysis pipeline automatically generates a summary, suggests a category, and detects any time-sensitive deadlines embedded in the page content. The PWA dashboard serves as the management layer: users can browse, search, filter, annotate, and set reminders on saved items from any device. An auto-cleanup system surfaces staleness reminders and archives untouched items to keep the library actionable.

## Tech Stack

| Component | Name + Version | Notes |
| --- | --- | --- |
| Extension frontend | React 18 + TypeScript 5 + Vite 5 | Chrome Manifest V3 extension popup and service worker |
| PWA frontend | React 18 + TypeScript 5 + Vite 5 | Dashboard SPA, installable as PWA |
| Client state management | Zustand 4 | Client-side UI state in the PWA |
| Server state management | TanStack React Query 5 | Server data fetching and cache management in the PWA |
| CSS framework | Tailwind CSS 3 | Utility-first styling; no CSS modules or styled-components |
| Frontend form validation | Zod 3 | Client-side schema validation |
| JavaScript runtime | Node.js 24.x LTS | Active LTS; required for Vite 5 and all frontend tooling |
| Package manager | npm 11.x | Ships with Node.js 24 LTS |
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

## Architecture

TabVault is organized into three layers: client, backend, and data.

**Client layer** comprises two frontend applications that share conventions but are deployed separately.

The Chrome Extension (Manifest V3) provides the capture UI via a popup and a background service worker. The service worker manages auth token storage and refresh; tokens are persisted in chrome.storage.local because MV3 service workers are ephemeral and cannot hold in-memory state reliably between invocations. The service worker sends save requests to the backend REST API and processes alarm events to display in-browser reminder notifications. The extension does not perform content extraction — it sends the URL and page title to the backend and receives the analysis result.

The PWA dashboard is a single-page React application that serves as the primary management interface for browsing, searching, editing, and organizing saved items. It is installable as a PWA on desktop and mobile. A service worker caches the app shell and previously loaded data for offline access. The Share Target API allows the installed PWA to receive URLs shared from other mobile apps; URLs received while the device is offline are queued in the service worker and submitted to the backend when connectivity is restored, consistent with the offline note queue behavior.

**Backend layer** is a single Spring Boot 3 application exposing a REST API consumed by both clients. It is organized into four internal service areas:

- Auth Service: handles registration, login, JWT issuance, and token refresh.
- Item Service: handles CRUD for items, categories, and tags. Orchestrates the content analysis pipeline by writing a job record to the `content_analysis_jobs` outbox table on item save, so analysis jobs survive service restarts. Updates `last_visited_at` on item click-through.
- LLM Service: reads pending jobs from the `content_analysis_jobs` table, checks the URL deduplication cache, calls the Claude API using the tool-use pattern, and returns structured analysis results (summary, category suggestion, detected deadlines) by updating the item record directly.
- Reminder Scheduler: evaluates upcoming reminders and staleness conditions on a daily schedule, creates staleness reminder records, and dispatches push notifications for due reminders via the Web Push API.

**Data layer**: PostgreSQL 16 is the primary store for all application data (users, items, categories, reminders, tags). The `content_analysis_jobs` outbox table tracks pending and failed analysis jobs with status, retry count, and last-attempted timestamp. Full-text search is served by PostgreSQL tsvector columns on item titles, summaries, and note bodies; these columns are maintained by PostgreSQL trigger functions using the `english` language configuration, so the index is updated both on initial item save and when the LLM summary is written asynchronously. Redis 7.2 provides the URL deduplication cache, rate-limit counters, and optionally session storage; Redis is optional for MVP deployment and can be replaced by a PostgreSQL-backed cache table during initial development.

**Type synchronization**: Java DTOs annotated with springdoc-openapi are the source-of-truth API contract. The backend auto-generates an OpenAPI spec at `/v3/api-docs`. The `openapi-typescript` tool generates TypeScript interfaces from that spec for use by both frontend applications.

**Shared Conventions**:

- All REST API error responses shall use a consistent JSON envelope: `{ "error": { "code": "ERROR_CODE", "message": "human-readable message", "field": "optional field name for validation errors" } }`.
- All async analysis jobs shall be tracked in the `content_analysis_jobs` outbox table (PostgreSQL-backed) and never held in-memory only, so jobs survive service restarts.
- Token estimation for LLM input truncation shall use the approximation: characters / 4 = estimated tokens. Truncation shall occur at the character level before sending to the API.
- The tsvector columns for full-text search shall be maintained by PostgreSQL trigger functions using the `english` language configuration, applied on both initial item save and LLM summary write.
- VAPID key pairs shall be injected as environment variables (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`) and never hardcoded or committed to source.
- Chrome Extension token storage shall use `chrome.storage.local` exclusively; tokens shall never be stored in memory only, given MV3 service worker lifecycle constraints.
- The Quartz job store shall be configured as JDBC (PostgreSQL-backed) in all environments; the in-memory store shall not be used because it does not survive service restarts.
- All modules shall follow feature-based directory structure: group files by module/feature, not by type (no top-level /controllers, /services, /repositories directories).

## Shared Conventions

_Build config not yet provided — PM must fill in status.md Build Config._

## Module Index

| MOD-ID | Module Name | Directory | Description |
| --- | --- | --- | --- |
| MOD-001 | Authentication | mod-auth | Manages user registration, login, JWT access token issuance, refresh token rotation, and logout. |
| MOD-002 | Item Management | mod-item-management | Manages CRUD operations for saved items (links, notes, videos), category management, tag management, pin and archive state, and the `last_visited_at` timestamp update on item click-through. |
| MOD-003 | Content Analysis Pipeline | mod-content-analysis | Reads pending job records from the `content_analysis_jobs` outbox table, calls the Claude API via the tool-use pattern to produce a summary, category suggestion, and deadline list, and writes structured results back to the item record. |
| MOD-004 | Content Extraction | mod-content-extraction | Extracts readable text or metadata from a URL so that MOD-003 can analyze it. |
| MOD-005 | Reminder Service | mod-reminder-service | Manages reminder CRUD for both manual and auto-detected deadline reminders, evaluates upcoming reminders on a schedule, and dispatches push notifications to the user's registered push subscriptions when a reminder is due. |
| MOD-006 | Auto-Cleanup Scheduler | mod-autocleanup | Runs daily scheduled jobs to identify non-pinned items not visited within the user's configured staleness threshold, creates staleness reminder records via MOD-005, and auto-archives items that pass the grace period without user interaction. |
| MOD-007 | Chrome Extension | mod-chrome-extension | Provides the browser capture UI (popup and background service worker) for saving tabs, saving all tabs, creating quick notes, viewing recent saves, and displaying in-browser reminder notifications. |
| MOD-008 | PWA Dashboard | mod-pwa-dashboard | Provides the management-layer web application where users browse, search, filter, edit, and organize saved items; manage categories; create and manage reminders; create plain text notes; and configure account settings including cleanup preferences. |
