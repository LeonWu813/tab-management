**Last Synced from PRD Revision**: 2 | **Last Updated**: 2026-05-30

---

## Module ID & Name

MOD-007: Chrome Extension

## Purpose

Provides the browser capture UI (popup and background service worker) for saving tabs, saving all tabs, creating quick notes, viewing recent saves, and displaying in-browser reminder notifications. Stores and refreshes auth tokens using `chrome.storage.local` exclusively; when a token refresh fails or the service worker wakes without a valid token, the popup displays a re-authentication prompt rather than silently failing. Communicates with the backend REST API exclusively — does not access the database directly.

## Context

**Business problem this module addresses:**

Enable users to save any browser tab in one action (click or keyboard shortcut) and have the tab's content automatically summarized and categorized within 10 seconds of saving, so users can close the tab immediately with no manual effort. Support for browsers other than Google Chrome is out of scope for v1. The extension targets Chrome Manifest V3 exclusively.

**Related user stories (full text):**

**US-001**: As a registered user, I want to save the current browser tab in one action via the extension (click or keyboard shortcut), so that I can close the tab immediately without losing the content or needing to remember to return to it.

**US-002**: As a registered user, I want to save all open tabs in the current browser window at once, so that I can close the entire window quickly when I need to free memory without manually saving each tab.

**US-007**: As a registered user, I want to manually create reminders on any saved item and manage (edit, snooze, dismiss) existing reminders, so that I am notified at the right time to act on time-sensitive saved content.

**US-008**: As a registered user, I want to create plain text notes from the extension popup or the dashboard, so that I can capture context or ideas alongside saved links without switching to a separate note-taking app.

**Non-goals from PRD that bound this module:**

- Support for browsers other than Google Chrome is out of scope for v1. The extension targets Chrome Manifest V3 exclusively. Firefox, Safari, and Edge extensions are not planned for this release.
- Native iOS and Android applications are out of scope for v1. Mobile access is provided via the responsive PWA only.

## Related User Stories: US-001, US-002, US-007, US-008

- US-001
- US-002
- US-007
- US-008

## Requirements

- The system shall display a list of the user's 5 most recently saved items in the extension popup, each showing the item title and relative saved timestamp.
- The system shall trigger the save-current-tab flow when the user activates the `Ctrl+Shift+S` shortcut on Windows or Linux, or `Cmd+Shift+S` on macOS.
- The system shall trigger the save-all-tabs flow for the current window when the user activates the `Ctrl+Shift+A` shortcut on Windows or Linux, or `Cmd+Shift+A` on macOS.
- The system shall open the quick note input in the extension popup when the user activates the `Ctrl+Shift+N` shortcut on Windows or Linux, or `Cmd+Shift+N` on macOS.
- The system shall read and write JWT access tokens and refresh tokens exclusively from `chrome.storage.local`; the service worker shall not hold token values in module-level or global variables that would be lost when the service worker is terminated between events.
- The system shall read the refresh token from `chrome.storage.local` and request a new access token from the backend refresh endpoint before issuing any API call when the service worker wakes and finds that the stored access token has expired or is absent.
- The system shall display a re-authentication prompt in the extension popup and clear the stored tokens from `chrome.storage.local` when a token refresh attempt returns HTTP 401, so the user can log in again without the popup being stuck in a broken authenticated state.

## Input / Output Contract

**Input:**

- User actions in the extension popup: save current tab, save all tabs, create quick note, view recent saves
- Keyboard shortcuts: `Ctrl+Shift+S` / `Cmd+Shift+S` (save current tab), `Ctrl+Shift+A` / `Cmd+Shift+A` (save all tabs), `Ctrl+Shift+N` / `Cmd+Shift+N` (open quick note input)
- Backend REST API responses: saved item records, auth tokens, reminder notifications
- `chrome.storage.local`: stored JWT access token and refresh token

**Output:**

- Save requests sent to backend REST API (single tab or batch)
- Note save requests sent to backend REST API
- Extension popup displays: 5 most recently saved items (title + relative saved timestamp), re-authentication prompt when token refresh returns HTTP 401, quick note input
- In-browser reminder notifications dispatched via service worker alarm events
- Token read/write: JWT access token and refresh token stored and retrieved exclusively from `chrome.storage.local`
- Token refresh: new access token requested from backend refresh endpoint when stored access token is expired or absent
- Token cleared from `chrome.storage.local` when HTTP 401 is returned on token refresh

## Dependencies

- MOD-001 (Authentication — login and token refresh)
- MOD-002 (Item Management — save and retrieve items)
- MOD-005 (Reminder Service — receive and dismiss notifications)

## Acceptance Criteria

- AC-048: The system shall display a list of the user's 5 most recently saved items in the extension popup, each showing the item title and relative saved timestamp.
- AC-049: The system shall trigger the save-current-tab flow when the user activates the `Ctrl+Shift+S` shortcut on Windows or Linux, or `Cmd+Shift+S` on macOS.
- AC-050: The system shall trigger the save-all-tabs flow for the current window when the user activates the `Ctrl+Shift+A` shortcut on Windows or Linux, or `Cmd+Shift+A` on macOS.
- AC-051: The system shall open the quick note input in the extension popup when the user activates the `Ctrl+Shift+N` shortcut on Windows or Linux, or `Cmd+Shift+N` on macOS.
- AC-052: The system shall read and write JWT access tokens and refresh tokens exclusively from `chrome.storage.local`; the service worker shall not hold token values in module-level or global variables that would be lost when the service worker is terminated between events.
- AC-053: The system shall read the refresh token from `chrome.storage.local` and request a new access token from the backend refresh endpoint before issuing any API call when the service worker wakes and finds that the stored access token has expired or is absent.
- AC-054: The system shall display a re-authentication prompt in the extension popup and clear the stored tokens from `chrome.storage.local` when a token refresh attempt returns HTTP 401, so the user can log in again without the popup being stuck in a broken authenticated state.
