**Last Synced from PRD Revision**: 2 | **Last Updated**: 2026-05-30

---

## Module ID & Name

MOD-004: Content Extraction

## Purpose

Extracts readable text or metadata from a URL so that MOD-003 can analyze it. Handles article pages (HTML parsing and content extraction), YouTube video transcripts (YouTube Data API v3), PDF links (PDFBox text extraction), and Open Graph metadata for non-YouTube video platform URLs. When a YouTube transcript is unavailable, stores the video title and thumbnail only and marks the summary as unavailable. Returns extracted text or metadata to MOD-002.

## Context

**Business problem this module addresses:**

Enable users to save any browser tab in one action (click or keyboard shortcut) and have the tab's content automatically summarized and categorized within 10 seconds of saving, so users can close the tab immediately with no manual effort. Keep LLM token costs within the defined per-item budget (2,500–3,500 input tokens, 200–400 output tokens) by enforcing content truncation and URL-level deduplication before every API call.

**Related user stories (full text):**

**US-001**: As a registered user, I want to save the current browser tab in one action via the extension (click or keyboard shortcut), so that I can close the tab immediately without losing the content or needing to remember to return to it.

**US-003**: As a registered user, I want saved links to be automatically summarized and assigned a suggested category, so that I can understand what a saved item is about at a glance without reopening the original URL.

**US-009**: As a registered user, I want YouTube video links I save to be automatically summarized using the video transcript, so that I can understand the video content without watching it in full.

**US-010**: As a registered user, I want links to non-YouTube video platforms (Instagram Reels, TikTok) to be saved with basic metadata, so that they appear in my dashboard with a title and thumbnail even when a full summary is unavailable.

**Non-goals from PRD that bound this module:**

- Audio extraction and speech-to-text summarization for non-YouTube video platforms (Instagram Reels, TikTok) are out of scope for v1. These platforms receive metadata storage only.
- Support for browsers other than Google Chrome is out of scope for v1.

## Related User Stories: US-001, US-003, US-009, US-010

- US-001
- US-003
- US-009
- US-010

## Requirements

- The system shall detect a YouTube URL by regex pattern match and retrieve the video transcript using the YouTube Data API v3.
- The system shall pass the retrieved YouTube transcript to MOD-003 for summarization, applying the same 3,000-token truncation limit as article content, when the transcript is successfully retrieved.
- The system shall store the video title, YouTube oEmbed thumbnail URL, platform identifier "youtube", and LLM-generated summary on the item record when a YouTube link is saved and analyzed.
- The system shall detect non-YouTube video platform URLs (Instagram, TikTok) by URL pattern and store the URL, page title from Open Graph tags, thumbnail from the `og:image` tag, and platform name without calling the Claude API.
- The system shall display the label "No summary available — open to watch" for non-YouTube video items in the dashboard.
- The system shall store the video title and YouTube oEmbed thumbnail URL on the item record and set the summary field to null when a YouTube transcript is unavailable for a saved YouTube link.
- The system shall display the label "Transcript unavailable — open to watch" for YouTube items whose summary field is null in the dashboard.

## Input / Output Contract

**Input:**

- URL of the saved item (from MOD-002 item save flow)
- URL type determined by pattern matching: article page, YouTube URL, PDF link, non-YouTube video platform URL (Instagram, TikTok)

**Output:**

- Article pages: extracted readable text (HTML parsed via Jsoup + Readability4J), returned to MOD-002
- YouTube links (transcript available): video title, YouTube oEmbed thumbnail URL, platform identifier "youtube", transcript text (truncated to 3,000 tokens), passed to MOD-003
- YouTube links (transcript unavailable): video title, YouTube oEmbed thumbnail URL on the item record; summary field set to null
- PDF links: extracted text (via PDFBox), returned to MOD-002
- Non-YouTube video platform URLs (Instagram, TikTok): URL, page title from Open Graph tags, thumbnail from `og:image` tag, platform name — stored without calling Claude API

## Dependencies

- MOD-002 (Item Management — called as part of the item save flow)

## Acceptance Criteria

- AC-028: The system shall detect a YouTube URL by regex pattern match and retrieve the video transcript using the YouTube Data API v3.
- AC-029: The system shall pass the retrieved YouTube transcript to MOD-003 for summarization, applying the same 3,000-token truncation limit as article content, when the transcript is successfully retrieved.
- AC-030: The system shall store the video title, YouTube oEmbed thumbnail URL, platform identifier "youtube", and LLM-generated summary on the item record when a YouTube link is saved and analyzed.
- AC-031: The system shall detect non-YouTube video platform URLs (Instagram, TikTok) by URL pattern and store the URL, page title from Open Graph tags, thumbnail from the `og:image` tag, and platform name without calling the Claude API.
- AC-032: The system shall display the label "No summary available — open to watch" for non-YouTube video items in the dashboard.
- AC-058: The system shall store the video title and YouTube oEmbed thumbnail URL on the item record and set the summary field to null when a YouTube transcript is unavailable for a saved YouTube link.
- AC-059: The system shall display the label "Transcript unavailable — open to watch" for YouTube items whose summary field is null in the dashboard.
