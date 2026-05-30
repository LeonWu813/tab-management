**Last Synced from PRD Revision**: 2 | **Last Updated**: 2026-05-30

---

## Module ID & Name

MOD-006: Auto-Cleanup Scheduler

## Purpose

Runs daily scheduled jobs to identify non-pinned items not visited within the user's configured staleness threshold, creates staleness reminder records via MOD-005, and auto-archives items that pass the grace period without user interaction. Respects per-user opt-out and pin settings. Each daily job run is idempotent: if a staleness reminder for an item already exists and has not been acted on, the job shall not create a duplicate reminder for that item. Does not dispatch push notifications directly.

## Context

**Business problem this module addresses:**

Keep the library actionable: an auto-cleanup system surfaces staleness reminders and archives untouched items to keep the library actionable.

**Related user stories (full text):**

**US-011**: As a registered user, I want to be reminded about saved items I have not visited in 30 days and to have them auto-archived if I do not act, so that my saved library stays manageable and does not accumulate forgotten, irrelevant items.

**US-012**: As a registered user, I want to pin important items to exempt them from auto-cleanup and to configure or disable the staleness threshold, so that I have full control over which items are protected and how aggressively the cleanup runs.

**Non-goals from PRD that bound this module:**

No non-goals stated in PRD for this module.

## Related User Stories: US-011, US-012

- US-011
- US-012

## Requirements

- The system shall create a staleness reminder with the label "You haven't revisited this in [N] days — still need it?" for each non-pinned, non-archived item whose `last_visited_at` (or `created_at` if never visited) is older than the user's configured staleness threshold (default: 30 days), evaluated once daily.
- The system shall auto-archive an item 7 days after its staleness reminder was dismissed without the user selecting "Keep" or visiting the item, provided the item has still not been visited during that 7-day period.
- The system shall clear any pending staleness reminder for an item and update `last_visited_at` to the current timestamp when the user opens the original URL from the item's dashboard entry or detail view.
- The system shall not update `last_visited_at` when the user scrolls past an item in the dashboard list view; only an explicit open of the original URL from the item's entry shall count as a visit.
- The system shall not create staleness reminders or auto-archive any item whose `is_pinned` flag is true, regardless of last visit time.
- The system shall apply an updated staleness threshold to the next daily scheduled job run when the user changes their staleness threshold setting to one of the allowed values: 14, 30, 60, or 90 days.
- The system shall not create any staleness reminders and shall not auto-archive any items for a user who has disabled auto-cleanup via the opt-out toggle in account settings.
- The system shall not create a new staleness reminder for an item when a staleness reminder for that item already exists with a status of "pending" or "pending confirmation" at the time the daily job runs for that item.

## Input / Output Contract

**Input:**

- Daily scheduled job trigger
- Item records from MOD-002: `last_visited_at`, `created_at`, `is_pinned`, archive status, per-user staleness threshold setting, per-user auto-cleanup opt-out flag

**Output:**

- Staleness reminder records created via MOD-005 (label: "You haven't revisited this in [N] days — still need it?") for qualifying non-pinned, non-archived items
- Item archive status updated to archived for items that pass the 7-day grace period without user interaction
- No output (job skipped) for items where: `is_pinned` is true, user has opted out of auto-cleanup, or a staleness reminder with status "pending" or "pending confirmation" already exists for that item

## Dependencies

- MOD-002 (Item Management — reads item state; updates archive status)
- MOD-005 (Reminder Service — creates staleness reminder records)

## Acceptance Criteria

- AC-033: The system shall create a staleness reminder with the label "You haven't revisited this in [N] days — still need it?" for each non-pinned, non-archived item whose `last_visited_at` (or `created_at` if never visited) is older than the user's configured staleness threshold (default: 30 days), evaluated once daily.
- AC-034: The system shall auto-archive an item 7 days after its staleness reminder was dismissed without the user selecting "Keep" or visiting the item, provided the item has still not been visited during that 7-day period.
- AC-035: The system shall clear any pending staleness reminder for an item and update `last_visited_at` to the current timestamp when the user opens the original URL from the item's dashboard entry or detail view.
- AC-036: The system shall not update `last_visited_at` when the user scrolls past an item in the dashboard list view; only an explicit open of the original URL from the item's entry shall count as a visit.
- AC-037: The system shall not create staleness reminders or auto-archive any item whose `is_pinned` flag is true, regardless of last visit time.
- AC-038: The system shall apply an updated staleness threshold to the next daily scheduled job run when the user changes their staleness threshold setting to one of the allowed values: 14, 30, 60, or 90 days.
- AC-039: The system shall not create any staleness reminders and shall not auto-archive any items for a user who has disabled auto-cleanup via the opt-out toggle in account settings.
- AC-066: The system shall not create a new staleness reminder for an item when a staleness reminder for that item already exists with a status of "pending" or "pending confirmation" at the time the daily job runs for that item.
