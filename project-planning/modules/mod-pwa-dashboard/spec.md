**Last Synced from PRD Revision**: 4 | **Last Updated**: 2026-06-19

---

## Module ID & Name

MOD-008: PWA Dashboard

## Purpose

Provides the management-layer web application where users browse, search, filter, edit, and organize saved items; manage categories; create and manage reminders; create plain text notes; and configure account settings including cleanup preferences. Implements PWA features: a service worker for offline caching of previously loaded data and the Share Target API for mobile URL sharing. URLs received via Share Target while the device is offline are queued in the service worker and submitted to the backend when connectivity is restored.

## Context

**Business problem this module addresses:**

Provide a searchable dashboard that returns matching saved items within 3 seconds of a search query, so users can reliably locate any previously saved item without reopening their browser history. Deliver a mobile-accessible dashboard so that users can review and manage saved items from a mobile browser or installed PWA without losing core functionality (browse, search, open original URL).

**Related user stories (full text):**

**US-005**: As a registered user, I want to browse my saved items in the dashboard and search or filter them by title, summary, category, type, or tag, so that I can quickly locate a specific saved item without scrolling through everything I have saved.

**US-006**: As a registered user, I want to create, rename, reorder, and delete categories, and reassign items between categories, so that I can organize my saved items according to my own classification scheme.

**US-007**: As a registered user, I want to manually create reminders on any saved item and manage (edit, snooze, dismiss) existing reminders, so that I am notified at the right time to act on time-sensitive saved content.

**US-008**: As a registered user, I want to create plain text notes from the extension popup or the dashboard, so that I can capture context or ideas alongside saved links without switching to a separate note-taking app.

**US-011**: As a registered user, I want to be reminded about saved items I have not visited in 30 days and to have them auto-archived if I do not act, so that my saved library stays manageable and does not accumulate forgotten, irrelevant items.

**US-012**: As a registered user, I want to pin important items to exempt them from auto-cleanup and to configure or disable the staleness threshold, so that I have full control over which items are protected and how aggressively the cleanup runs.

**US-013**: As a registered user, I want the PWA dashboard to remain usable for browsing previously loaded items when I am offline, so that I can review saved content even without an internet connection.

**US-014**: As a registered user on a mobile device, I want to share a URL from any mobile app directly to TabVault via the system share sheet, so that I can save content on mobile without needing to open a desktop browser.

**Non-goals from PRD that bound this module:**

- Native iOS and Android applications are out of scope for v1. Mobile access is provided via the responsive PWA only.
- AI-powered semantic (embedding-based) search is out of scope for v1. Full-text search via PostgreSQL is the only search mechanism for this release.
- LLM-suggested smart groupings across saved items are out of scope for v1.
- Internationalization and localization are out of scope for v1. The product is US English only.

## Related User Stories: US-005, US-006, US-007, US-008, US-011, US-012, US-013, US-014

- US-005
- US-006
- US-007
- US-008
- US-011
- US-012
- US-013
- US-014

## Requirements

- The system shall display only items matching the active filter criteria (category, content type, date range, or tag) when one or more filter controls are applied in the dashboard.
- The system shall return matching saved items within 3 seconds of a search query being submitted, searching across item titles, summaries, and note body text.
- The system shall allow a user to toggle the dashboard display between grid view and list view, and shall persist the selected view preference across sessions for that user.
- The system shall allow inline editing of an item's title, summary, and category assignment from the item card and save the change without requiring navigation to a separate detail page.
- The system shall serve previously loaded saved items and the app shell from the service worker cache and display them to the user when the PWA dashboard is opened without an internet connection.
- The system shall queue a note creation request in the service worker and submit it to the backend when connectivity is restored, when a user creates a note while offline.
- The system shall register as a Share Target so that users can share a URL to TabVault from any mobile app via the native share sheet on a device with the PWA installed.
- The system shall save the shared URL as a new item and trigger the content analysis pipeline when a URL is received via the Share Target.
- The system shall queue a Share Target URL in the service worker using the same offline queue mechanism as AC-041 and submit it to the backend when connectivity is restored, when a URL is received via the Share Target while the device has no internet connectivity.
- The system shall display a confirmation prompt when the user activates the delete action on an item card, and shall call DELETE /api/items/{id} and remove the item from the dashboard view on confirmation.
- The system shall group items by their assigned category in the dashboard view, displaying each group under a labeled section header; items with no assigned category shall appear under an "Uncategorized" group; each group section shall be collapsible.

## Input / Output Contract

**Input:**

- User search queries: text submitted in the dashboard search field
- Filter selections: category, content type, date range, or tag
- View preference toggle: grid view or list view
- Inline edit requests: updated item title, summary, or category assignment
- Offline note creation: note body text submitted while device has no internet connectivity
- Share Target URL: URL received from any mobile app via the native system share sheet
- Share Target URL received offline: URL received via Share Target while device has no internet connectivity
- Delete item action: user activates delete on an item card and confirms the confirmation prompt
- Category grouping toggle: dashboard groups items by assigned category

**Output:**

- Filtered item list: only items matching active filter criteria displayed
- Search results: matching saved items returned within 3 seconds, searching across item titles, summaries, and note body text
- View preference persisted across sessions for the user
- Inline edit: item record updated and reflected in item card without page navigation
- Offline display: previously loaded saved items and app shell served from service worker cache
- Offline note queue: note creation request queued in service worker, submitted to backend on connectivity restoration
- Share Target registration: PWA registered as a Share Target on devices with the PWA installed
- Share Target save: shared URL saved as new item; content analysis pipeline triggered
- Share Target offline queue: Share Target URL queued in service worker using same mechanism as offline note queue; submitted to backend when connectivity is restored
- Delete item: item removed from dashboard view after user confirms deletion; DELETE /api/items/{id} called
- Category groups: dashboard displays items grouped by category under labeled section headers; items with no category appear under "Uncategorized"; each group is collapsible

## Dependencies

- MOD-001 (Authentication)
- MOD-002 (Item Management)
- MOD-005 (Reminder Service)
- MOD-006 (Auto-Cleanup Scheduler — user preference settings exposed via the settings UI)

## Acceptance Criteria

- AC-014: The system shall display only items matching the active filter criteria (category, content type, date range, or tag) when one or more filter controls are applied in the dashboard.
- AC-015: The system shall return matching saved items within 3 seconds of a search query being submitted, searching across item titles, summaries, and note body text.
- AC-016: The system shall allow a user to toggle the dashboard display between grid view and list view, and shall persist the selected view preference across sessions for that user.
- AC-017: The system shall allow inline editing of an item's title, summary, and category assignment from the item card and save the change without requiring navigation to a separate detail page.
- AC-040: The system shall serve previously loaded saved items and the app shell from the service worker cache and display them to the user when the PWA dashboard is opened without an internet connection.
- AC-041: The system shall queue a note creation request in the service worker and submit it to the backend when connectivity is restored, when a user creates a note while offline.
- AC-042: The system shall register as a Share Target so that users can share a URL to TabVault from any mobile app via the native share sheet on a device with the PWA installed.
- AC-043: The system shall save the shared URL as a new item and trigger the content analysis pipeline when a URL is received via the Share Target.
- AC-064: The system shall queue a Share Target URL in the service worker using the same offline queue mechanism as AC-041 and submit it to the backend when connectivity is restored, when a URL is received via the Share Target while the device has no internet connectivity.
- AC-067: The system shall display a confirmation prompt when the user activates the delete action on an item card, and shall call `DELETE /api/items/{id}` and remove the item from the dashboard view on confirmation.
- AC-068: The system shall group items by their assigned category in the dashboard view, displaying each group under a labeled section header; items with no assigned category shall appear under an "Uncategorized" group; each group section shall be collapsible.
