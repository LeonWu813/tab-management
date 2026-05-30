**Last Synced from PRD Revision**: 2 | **Last Updated**: 2026-05-30

---

## Module ID & Name

MOD-002: Item Management

## Purpose

Manages CRUD operations for saved items (links, notes, videos), category management, tag management, pin and archive state, and the `last_visited_at` timestamp update on item click-through. Orchestrates calls to MOD-004 (content extraction) and MOD-003 (content analysis) after a new item is saved by writing a job record to the `content_analysis_jobs` outbox table. Enforces per-user hourly rate limits on batch-save requests. Does not perform extraction or LLM calls directly.

## Context

**Business problem this module addresses:**

Enable users to save any browser tab in one action (click or keyboard shortcut) and have the tab's content automatically summarized and categorized within 10 seconds of saving, so users can close the tab immediately with no manual effort. Provide a searchable dashboard that returns matching saved items within 3 seconds of a search query, so users can reliably locate any previously saved item without reopening their browser history. Keep LLM token costs within the defined per-item budget (2,500–3,500 input tokens, 200–400 output tokens) by enforcing content truncation and URL-level deduplication before every API call.

**Related user stories (full text):**

**US-001**: As a registered user, I want to save the current browser tab in one action via the extension (click or keyboard shortcut), so that I can close the tab immediately without losing the content or needing to remember to return to it.

**US-002**: As a registered user, I want to save all open tabs in the current browser window at once, so that I can close the entire window quickly when I need to free memory without manually saving each tab.

**US-005**: As a registered user, I want to browse my saved items in the dashboard and search or filter them by title, summary, category, type, or tag, so that I can quickly locate a specific saved item without scrolling through everything I have saved.

**US-006**: As a registered user, I want to create, rename, reorder, and delete categories, and reassign items between categories, so that I can organize my saved items according to my own classification scheme.

**US-008**: As a registered user, I want to create plain text notes from the extension popup or the dashboard, so that I can capture context or ideas alongside saved links without switching to a separate note-taking app.

**Non-goals from PRD that bound this module:**

- AI-powered semantic (embedding-based) search is out of scope for v1. Full-text search via PostgreSQL is the only search mechanism for this release.
- LLM-suggested smart groupings across saved items are out of scope for v1.
- A freemium billing or payment integration is out of scope for v1. Cost controls are implemented as server-side rate limits only.
- API versioning with backward compatibility guarantees across releases is out of scope for v1.

## Related User Stories: US-001, US-002, US-005, US-006, US-008

- US-001
- US-002
- US-005
- US-006
- US-008

## Requirements

- The system shall create a new item record containing the URL, page title, favicon URL, and `created_at` timestamp when the extension submits a valid save request for the current tab.
- The system shall return the saved item record to the extension within 2 seconds of the save request being received, so the extension can display a confirmation toast without waiting for LLM analysis to complete.
- The system shall close the saved tab in the browser when the user has enabled the "close tab on save" option and the save request returns a success response.
- The system shall return HTTP 200 with the existing item record when a save request is received for a URL already saved by the same user and not yet deleted or archived, preventing duplicate items.
- The system shall accept a batch save request containing all URLs from the current browser window and enqueue each URL for sequential processing, returning HTTP 202 immediately to the extension.
- The system shall save all items from a batch request even when content extraction or LLM analysis fails for individual items; items with extraction or analysis failures shall be saved with the URL and page title only, with summary and category fields left blank.
- The system shall allow a user to create a category with a name of 1 to 50 characters, a hex color code, and an optional icon, and shall return the created category in the response.
- The system shall reassign all items in a deleted category to "uncategorized" rather than deleting the items when a category is deleted.
- The system shall update an item's category assignment and return the updated item record when the user submits a category reassignment request for an item they own.
- The system shall create a plain text note item with the user-provided body text when a note save request is submitted from the extension popup or the dashboard.
- The system shall store the note body as plain text without modifying, sanitizing, or interpreting the user's input.
- The system shall return note items in search results when the search query matches text in the note body.
- The system shall reject a batch save request with HTTP 429 when the requesting user has submitted more than 100 tab URLs to the batch-save endpoint within the current rolling 60-minute window.

## Input / Output Contract

**Input:**

- Single-tab save request: URL, page title, favicon URL; user identity via authentication token
- Batch save request: array of URLs from the current browser window; user identity via authentication token
- Category create request: name (1–50 characters), hex color code, optional icon
- Category delete request: category ID; user identity via authentication token
- Category reassignment request: item ID, target category ID; user identity via authentication token
- Note save request: note body text (plain text); submitted from extension popup or dashboard; user identity via authentication token

**Output:**

- Single-tab save (new): saved item record (URL, page title, favicon URL, `created_at` timestamp), returned within 2 seconds
- Single-tab save (duplicate): HTTP 200 with existing item record
- Batch save: HTTP 202 immediately; each item processed sequentially
- Batch save rate-limit exceeded: HTTP 429
- Category create: created category record
- Category delete: all items reassigned to "uncategorized"
- Category reassignment: updated item record
- Note save: created note item record

## Dependencies

- MOD-001 (Authentication — user identity required for all item writes)

## Acceptance Criteria

- AC-001: The system shall create a new item record containing the URL, page title, favicon URL, and `created_at` timestamp when the extension submits a valid save request for the current tab.
- AC-002: The system shall return the saved item record to the extension within 2 seconds of the save request being received, so the extension can display a confirmation toast without waiting for LLM analysis to complete.
- AC-003: The system shall close the saved tab in the browser when the user has enabled the "close tab on save" option and the save request returns a success response.
- AC-004: The system shall return HTTP 200 with the existing item record when a save request is received for a URL already saved by the same user and not yet deleted or archived, preventing duplicate items.
- AC-005: The system shall accept a batch save request containing all URLs from the current browser window and enqueue each URL for sequential processing, returning HTTP 202 immediately to the extension.
- AC-006: The system shall save all items from a batch request even when content extraction or LLM analysis fails for individual items; items with extraction or analysis failures shall be saved with the URL and page title only, with summary and category fields left blank.
- AC-018: The system shall allow a user to create a category with a name of 1 to 50 characters, a hex color code, and an optional icon, and shall return the created category in the response.
- AC-019: The system shall reassign all items in a deleted category to "uncategorized" rather than deleting the items when a category is deleted.
- AC-020: The system shall update an item's category assignment and return the updated item record when the user submits a category reassignment request for an item they own.
- AC-025: The system shall create a plain text note item with the user-provided body text when a note save request is submitted from the extension popup or the dashboard.
- AC-026: The system shall store the note body as plain text without modifying, sanitizing, or interpreting the user's input.
- AC-027: The system shall return note items in search results when the search query matches text in the note body.
- AC-065: The system shall reject a batch save request with HTTP 429 when the requesting user has submitted more than 100 tab URLs to the batch-save endpoint within the current rolling 60-minute window.
