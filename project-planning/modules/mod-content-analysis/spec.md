**Last Synced from PRD Revision**: 2 | **Last Updated**: 2026-05-30

---

## Module ID & Name

MOD-003: Content Analysis Pipeline

## Purpose

Reads pending job records from the `content_analysis_jobs` outbox table, calls the Claude API via the tool-use pattern to produce a summary, category suggestion, and deadline list, and writes structured results back to the item record. Checks the URL deduplication cache before calling the API and writes results to the cache afterward. Retries failed jobs up to the defined retry limit before marking them as permanently failed. Invokes note categorization only when explicitly requested for a note longer than 50 words.

## Context

**Business problem this module addresses:**

Enable users to save any browser tab in one action (click or keyboard shortcut) and have the tab's content automatically summarized and categorized within 10 seconds of saving, so users can close the tab immediately with no manual effort. Surface time-sensitive content automatically: when a saved page contains a deadline, the system shall detect it and create a suggested reminder requiring no manual date entry by the user. Keep LLM token costs within the defined per-item budget (2,500–3,500 input tokens, 200–400 output tokens) by enforcing content truncation and URL-level deduplication before every API call.

**Related user stories (full text):**

**US-003**: As a registered user, I want saved links to be automatically summarized and assigned a suggested category, so that I can understand what a saved item is about at a glance without reopening the original URL.

**US-004**: As a registered user, I want the system to detect time-sensitive dates in saved pages and suggest reminders, so that I do not miss application deadlines, event registrations, or other time-sensitive actions embedded in content I have saved.

**Non-goals from PRD that bound this module:**

- AI-powered semantic (embedding-based) search is out of scope for v1. Full-text search via PostgreSQL is the only search mechanism for this release.
- LLM-suggested smart groupings across saved items are out of scope for v1.
- Audio extraction and speech-to-text summarization for non-YouTube video platforms (Instagram Reels, TikTok) are out of scope for v1. These platforms receive metadata storage only.

## Related User Stories: US-003, US-004

- US-003
- US-004

## Requirements

- The system shall send a Claude API request using the tool-use pattern with the `generate_summary` and `categorize_content` tools within 5 seconds of a new link item being created.
- The system shall truncate the extracted page text to a maximum of 3,000 tokens before including it in the Claude API request.
- The system shall skip the Claude API call and return the cached analysis result when the saved URL matches an entry in the URL deduplication cache.
- The system shall store the LLM-returned summary text, suggested category name, and content type on the item record and include them in the response to the client when analysis completes.
- The system shall invoke the `extract_deadlines` tool only when the LLM determines that time-sensitive content is present in the page text; the tool shall not be invoked on pages that contain no dates or deadlines.
- The system shall create a suggested reminder record with the detected date, label, and urgency level for each deadline returned by the `extract_deadlines` tool, with the reminder status set to "pending confirmation."
- The system shall not activate a reminder created from a detected deadline until the user has explicitly confirmed, modified, or accepted it; a pending-confirmation reminder shall not trigger push notifications.
- The system shall create a `content_analysis_jobs` record with status "pending" for a saved item at the time the item record is written to the database, before the save response is returned to the client.
- The system shall retry a failed analysis job up to 3 times, updating the `retry_count` and `last_attempted_at` fields on each attempt, before setting the job status to "failed" and leaving the item without a summary.
- The system shall guarantee at-least-once delivery of analysis jobs by polling the `content_analysis_jobs` table for pending and retryable records; a job shall not be considered complete until the item record has been updated with the LLM result and the job status has been set to "completed."

## Input / Output Contract

**Input:**

- Pending `content_analysis_jobs` records polled from the outbox table (status "pending" or retryable); each record references an item record and its extracted text or metadata
- URL deduplication cache: URL key lookup before every Claude API call

**Output:**

- Updated item record: LLM-returned summary text, suggested category name, content type
- Updated `content_analysis_jobs` record: status set to "completed"; `retry_count` and `last_attempted_at` updated on each retry attempt; status set to "failed" after 3 failed retries
- Suggested reminder record (when `extract_deadlines` tool returns deadlines): detected date, label, urgency level, reminder status "pending confirmation"
- URL deduplication cache: analysis result written to cache after a successful Claude API call

## Dependencies

- MOD-001 (Authentication — provides the user's existing category list for prompt context)
- MOD-002 (Item Management — provides the item record and extracted text via the outbox table; receives analysis results written back to the item record)

## Acceptance Criteria

- AC-007: The system shall send a Claude API request using the tool-use pattern with the `generate_summary` and `categorize_content` tools within 5 seconds of a new link item being created.
- AC-008: The system shall truncate the extracted page text to a maximum of 3,000 tokens before including it in the Claude API request.
- AC-009: The system shall skip the Claude API call and return the cached analysis result when the saved URL matches an entry in the URL deduplication cache.
- AC-010: The system shall store the LLM-returned summary text, suggested category name, and content type on the item record and include them in the response to the client when analysis completes.
- AC-011: The system shall invoke the `extract_deadlines` tool only when the LLM determines that time-sensitive content is present in the page text; the tool shall not be invoked on pages that contain no dates or deadlines.
- AC-012: The system shall create a suggested reminder record with the detected date, label, and urgency level for each deadline returned by the `extract_deadlines` tool, with the reminder status set to "pending confirmation."
- AC-013: The system shall not activate a reminder created from a detected deadline until the user has explicitly confirmed, modified, or accepted it; a pending-confirmation reminder shall not trigger push notifications.
- AC-055: The system shall create a `content_analysis_jobs` record with status "pending" for a saved item at the time the item record is written to the database, before the save response is returned to the client.
- AC-056: The system shall retry a failed analysis job up to 3 times, updating the `retry_count` and `last_attempted_at` fields on each attempt, before setting the job status to "failed" and leaving the item without a summary.
- AC-057: The system shall guarantee at-least-once delivery of analysis jobs by polling the `content_analysis_jobs` table for pending and retryable records; a job shall not be considered complete until the item record has been updated with the LLM result and the job status has been set to "completed."
