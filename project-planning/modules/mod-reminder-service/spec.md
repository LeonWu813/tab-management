**Last Synced from PRD Revision**: 2 | **Last Updated**: 2026-05-30

---

## Module ID & Name

MOD-005: Reminder Service

## Purpose

Manages reminder CRUD for both manual and auto-detected deadline reminders, evaluates upcoming reminders on a schedule, and dispatches push notifications to the user's registered push subscriptions when a reminder is due. Manages the full push subscription lifecycle: client subscription registration, VAPID key configuration, stale endpoint cleanup (410 Gone), and multi-device subscription records per user. Does not detect deadlines in content — that responsibility belongs to MOD-003.

## Context

**Business problem this module addresses:**

Surface time-sensitive content automatically: when a saved page contains a deadline, the system shall detect it and create a suggested reminder requiring no manual date entry by the user. Deliver a mobile-accessible dashboard so that users can review and manage saved items from a mobile browser or installed PWA without losing core functionality (browse, search, open original URL).

**Related user stories (full text):**

**US-004**: As a registered user, I want the system to detect time-sensitive dates in saved pages and suggest reminders, so that I do not miss application deadlines, event registrations, or other time-sensitive actions embedded in content I have saved.

**US-007**: As a registered user, I want to manually create reminders on any saved item and manage (edit, snooze, dismiss) existing reminders, so that I am notified at the right time to act on time-sensitive saved content.

**Non-goals from PRD that bound this module:**

- Native iOS and Android applications are out of scope for v1. Mobile access is provided via the responsive PWA only.

## Related User Stories: US-004, US-007

- US-004
- US-007

## Requirements

- The system shall create a reminder for any item the user owns when a manual reminder request is submitted with a valid future due date and an optional label.
- The system shall dispatch a push notification to all registered push subscriptions for the user when a reminder's due time is reached, containing the item title and reminder label.
- The system shall allow a user to dismiss a reminder or update its due date and label when an update request is submitted for a reminder the user owns.
- The system shall display a badge indicator on the item card in the dashboard when the item has a reminder due within the next 24 hours.
- The system shall store a push subscription record containing the endpoint URL, auth key, and p256dh key for a user's device when the client submits a push subscription registration request after the user grants browser push permission.
- The system shall read the VAPID public and private keys from the `VAPID_PUBLIC_KEY` and `VAPID_PRIVATE_KEY` environment variables when signing push notification requests; the system shall fail to start if either variable is absent or empty.
- The system shall delete the push subscription record for an endpoint when the Web Push service returns HTTP 410 Gone for that endpoint, and shall not attempt further delivery to that endpoint.
- The system shall store and dispatch to all active push subscription records associated with a user's account when sending a push notification, so a user with multiple registered devices receives the notification on each device with a valid subscription.

## Input / Output Contract

**Input:**

- Manual reminder create request: item ID (must be owned by the user), valid future due date, optional label; user identity via authentication token
- Reminder update request: reminder ID (must be owned by the user), updated due date and/or label, or dismiss action; user identity via authentication token
- Push subscription registration request: endpoint URL, auth key, p256dh key; submitted after user grants browser push permission; user identity via authentication token
- Scheduled evaluation: due reminders evaluated on a schedule; staleness reminder creation requests from MOD-006

**Output:**

- Reminder create: created reminder record
- Reminder update/dismiss: updated or dismissed reminder record
- Push notification dispatch: notification sent to all active push subscription records for the user, containing item title and reminder label
- Push subscription stored: push subscription record (endpoint URL, auth key, p256dh key) stored per device per user
- 410 Gone cleanup: push subscription record deleted for the stale endpoint; no further delivery attempts to that endpoint
- Dashboard badge: badge indicator on item card when item has a reminder due within the next 24 hours

## Dependencies

- MOD-001 (Authentication — user identity for reminder ownership)
- MOD-002 (Item Management — item association for each reminder)
- MOD-003 (Content Analysis Pipeline — provides detected deadlines that seed suggested reminders)

## Acceptance Criteria

- AC-021: The system shall create a reminder for any item the user owns when a manual reminder request is submitted with a valid future due date and an optional label.
- AC-022: The system shall dispatch a push notification to all registered push subscriptions for the user when a reminder's due time is reached, containing the item title and reminder label.
- AC-023: The system shall allow a user to dismiss a reminder or update its due date and label when an update request is submitted for a reminder the user owns.
- AC-024: The system shall display a badge indicator on the item card in the dashboard when the item has a reminder due within the next 24 hours.
- AC-060: The system shall store a push subscription record containing the endpoint URL, auth key, and p256dh key for a user's device when the client submits a push subscription registration request after the user grants browser push permission.
- AC-061: The system shall read the VAPID public and private keys from the `VAPID_PUBLIC_KEY` and `VAPID_PRIVATE_KEY` environment variables when signing push notification requests; the system shall fail to start if either variable is absent or empty.
- AC-062: The system shall delete the push subscription record for an endpoint when the Web Push service returns HTTP 410 Gone for that endpoint, and shall not attempt further delivery to that endpoint.
- AC-063: The system shall store and dispatch to all active push subscription records associated with a user's account when sending a push notification, so a user with multiple registered devices receives the notification on each device with a valid subscription.
