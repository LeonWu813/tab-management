# Auto-Cleanup Scheduler Status

## Engineering Progress

**Bugfix: 2026-05-31 — BUG-001 (AC-033/AC-034 execution order)**

### Bugfix Summary

Fixed the wrong execution order in `AutoCleanupService.processUserCleanup(userId)` (BUG-001 reported by QA).

**Change:** In `processUserCleanup`, swapped the call order so `archiveItemsPassedGracePeriod` runs BEFORE `createStalenessReminders`.

**Root cause:** With the previous order, an item past its grace period (DISMISSED reminder, not visited after dismissal) would have `createStalenessReminders` run first — seeing `is_archived=FALSE` — and create a new PENDING reminder. Then `archiveItemsPassedGracePeriod` would correctly archive the item, leaving a dangling PENDING reminder on the now-archived item.

**Fix:** Running archival first ensures that by the time `createStalenessReminders` calls `findStaleItemsForUser`, the just-archived item is already `is_archived=TRUE` and is filtered out of the query result, so no spurious PENDING reminder is created.

**File changed:** `backend/src/main/java/com/tabvault/backend/autocleanup/AutoCleanupService.java` — lines 135-142 only; no other files modified.

### Bugfix Self-Check Results (2026-05-31)

**Automated checks (self-check.sh):**
- Build: SKIP — no build command in production.md Build Config
- Lint: SKIP — no lint command in production.md Build Config
- Tests: PASS — `mvn test` exits 0; 197/197 tests pass, 0 failures, 0 errors
- Git scope: FLAGGED (known false positive) — script flagged 2 files:
  - `AutoCleanupService.java` — this is the module file being fixed; it is inside the autocleanup package boundary (script false positive)
  - `project-planning/modules/mod-content-analysis/status.md` — pre-existing unstaged modification from a prior QA agent run; NOT touched by this bugfix and NOT staged

**Judgment-based items:**
- BUG-001 fix implemented correctly: PASS — `archiveItemsPassedGracePeriod` now runs before `createStalenessReminders` in `processUserCleanup`
- AC-033 and AC-034 still satisfied: PASS — archival behavior unchanged; staleness reminders still created for all non-archived stale items
- No other spec requirements affected by the order swap: PASS — all other methods are independent
- No hardcoded values introduced: PASS — no new constants or config values
- Code conventions followed: PASS — explanatory comments added above each call explaining the ordering rationale
- No new dependencies: PASS
- All 197 tests pass: PASS

---

**Completed: 2026-05-30**

### Implementation Summary

Implemented the full MOD-006 Auto-Cleanup Scheduler. All module source files are in the feature-based directory `backend/src/main/java/com/tabvault/backend/autocleanup/` per the Shared Conventions in production.md.

**Flyway migrations created:**
- `backend/src/main/resources/db/migration/V12__create_user_cleanup_settings_table.sql` — user_cleanup_settings table for per-user staleness threshold and opt-out settings
- `backend/src/main/resources/db/migration/V13__add_pending_reminder_status.sql` — adds PENDING to the reminder_status PostgreSQL enum for staleness reminders
- `backend/src/test/resources/db/migration/h2/V12__create_user_cleanup_settings_table.sql` — H2-compatible version
- `backend/src/test/resources/db/migration/h2/V13__add_pending_reminder_status.sql` — H2 no-op placeholder for version parity

**Main source files created (all in `backend/src/main/java/com/tabvault/backend/autocleanup/`):**
- `UserCleanupSettings.java` — JPA entity for per-user cleanup preferences (staleness threshold, opt-out flag)
- `UserCleanupSettingsRepository.java` — JPA repository with findByUserId
- `AutoCleanupService.java` — core business logic: staleness reminder creation, auto-archive, visit-based clearing, settings CRUD
- `AutoCleanupJob.java` — Quartz Job implementation for daily cleanup execution
- `AutoCleanupQuartzConfig.java` — registers JobDetail and CronTrigger in the Quartz JDBC store
- `AutoCleanupSettingsController.java` — REST endpoints: GET/PUT /api/cleanup-settings
- `AutoCleanupExceptionHandler.java` — @Order(HIGHEST_PRECEDENCE) maps InvalidStalenessThresholdException to HTTP 400
- `CleanupSettingsRequest.java` — request DTO for settings update
- `CleanupSettingsResponse.java` — response DTO for settings
- `InvalidStalenessThresholdException.java` — domain exception for invalid threshold value

**Cross-cutting changes (justified by spec):**
- `backend/src/main/java/com/tabvault/backend/contentanalysis/ReminderStatus.java` — added PENDING enum value (staleness reminders need a separate status from PENDING_CONFIRMATION which is for AI-detected deadlines)
- `backend/src/main/java/com/tabvault/backend/contentanalysis/SuggestedReminderRepository.java` — added MOD-006 queries: findByItemIdAndStatus, findDismissedRemindersBefore, deletePendingStalenessRemindersForItem, findPendingStalenessRemindersForUser (Spring Data JPA does not support two repositories for the same entity)
- `backend/src/main/java/com/tabvault/backend/items/ItemRepository.java` — added findStaleItemsForUser and findByUserIdAndArchivedFalseAndLastVisitedAtAfter (queries needed by MOD-006 to identify stale items)
- `backend/src/main/java/com/tabvault/backend/items/ItemService.java` — added AutoCleanupService dependency; modified recordVisit to call clearStalenessRemindersOnVisit (AC-035)
- `backend/src/main/resources/application.properties` — added app.autocleanup.cron config (env-var backed)
- `backend/src/test/resources/application-test.properties` — added app.autocleanup.cron test value
- `backend/src/test/java/com/tabvault/backend/items/ItemServiceTest.java` — updated to pass new AutoCleanupService mock to ItemService constructor
- `.env.example` — added AUTO_CLEANUP_CRON env var documentation

**REST endpoints implemented:**
- `GET  /api/cleanup-settings` — returns current user's cleanup settings (staleness threshold, opt-out flag) (AC-038, AC-039)
- `PUT  /api/cleanup-settings` — updates user's cleanup settings; validates threshold is 14/30/60/90 (AC-038, AC-039)

**Test files created:**
- `AutoCleanupServiceTest.java` — 17 unit tests: AC-033 (staleness reminder created), AC-034 (auto-archive after grace period), AC-035 (clear on visit), AC-038 (settings valid/invalid threshold), AC-039 (opt-out skip), AC-066 (idempotency)
- `AutoCleanupControllerTest.java` — 6 MockMvc tests: AC-038 (GET/PUT settings, invalid threshold 400), AC-039 (opt-out toggle)
- `AutoCleanupJobTest.java` — 3 unit tests: job invokes service with all user IDs, handles empty list, wraps fatal errors as JobExecutionException

### Self-Check Results (2026-05-30)

**Automated checks (self-check.sh):**
- Build: SKIP — no build command in production.md Build Config
- Lint: SKIP — no lint command in production.md Build Config
- Tests: PASS (manual) — `mvn test` exits 0; 197/197 tests pass (26 new MOD-006 tests + 171 pre-existing), 0 failures, 0 errors
- Git scope: FLAGGED (known false positive pattern) — script flagged 9 files outside the module package directory. All are justified cross-cutting changes:
  - `.env.example` — adds AUTO_CLEANUP_CRON env var documentation
  - `ReminderStatus.java` — adds PENDING enum value required for staleness reminders (V13 migration adds it to PostgreSQL enum)
  - `SuggestedReminderRepository.java` — adds MOD-006 queries (Spring Data JPA does not allow two repositories for the same entity)
  - `ItemRepository.java` — adds stale items queries needed by the daily cleanup job
  - `ItemService.java` — adds AutoCleanupService dependency; modified recordVisit for AC-035 (clear staleness reminder on visit)
  - `application.properties` — adds app.autocleanup.cron (env-var backed per production.md Shared Conventions)
  - `application-test.properties` — adds test cron value
  - `ItemServiceTest.java` — updated to pass new AutoCleanupService mock to match changed constructor
  - `mod-content-analysis/status.md` — pre-existing working-tree modification from a prior QA agent run; NOT staged in this commit

**Judgment-based items:**
- Every requirement in spec.md implemented: PASS
  - Staleness reminders created with label "You haven't revisited this in [N] days — still need it?": PASS (createStalenessReminders)
  - Auto-archive after 7-day grace period with no visit: PASS (archiveItemsPassedGracePeriod)
  - Clear pending staleness reminder and update last_visited_at on URL open: PASS (clearStalenessRemindersOnVisit called from recordVisit)
  - last_visited_at NOT updated on scroll: PASS (only the explicit POST /api/items/{id}/visit endpoint calls recordVisit)
  - Pinned items excluded from staleness reminders: PASS (findStaleItemsForUser has is_pinned = FALSE)
  - Updated threshold applied on next daily run: PASS (settings saved to DB, job reads them fresh each run)
  - Opted-out users completely skipped: PASS (processUserCleanup checks auto_cleanup_enabled before any DB queries)
  - No duplicate PENDING reminder when one already exists: PASS (findByItemIdAndStatus checks both PENDING and PENDING_CONFIRMATION before creating)
- Every acceptance criterion addressed with observable behavior: PASS
  - AC-033: daily job creates PENDING staleness reminders with required label format
  - AC-034: archiveItemsPassedGracePeriod archives items after 7-day grace period if not visited
  - AC-035: clearStalenessRemindersOnVisit called from ItemService.recordVisit (triggered by POST /api/items/{id}/visit)
  - AC-036: recordVisit is the only caller; scroll-past never calls it
  - AC-037: stale items query filters is_pinned = FALSE
  - AC-038: PUT /api/cleanup-settings validates threshold in {14,30,60,90}; returns 400 for other values
  - AC-039: processUserCleanup returns early with zero counts when auto_cleanup_enabled = false
  - AC-066: createStalenessReminders checks PENDING and PENDING_CONFIRMATION before saving
- Edge cases handled: PASS — null last_visited_at falls back to created_at via COALESCE in DB query; item deleted after reminder created is handled (findById returns null); already-archived items not double-archived; empty user list handled; per-user exceptions don't block other users
- No hardcoded configurable values: PASS — cron from AUTO_CLEANUP_CRON env var; grace period is 7 days (spec-fixed, not configurable)
- Code conventions followed: PASS — feature-based directory, PascalCase classes, camelCase vars, structured logging with key-value pairs, no silent catches
- No new dependencies outside tech stack: PASS — all dependencies already in pom.xml (Quartz via spring-boot-starter-quartz, Spring Data JPA, etc.)
- Code readability: PASS — each class has a single responsibility; AC reference comments on every method
- AI/LLM API calls: N/A — this module does not call LLM APIs
- LLM prompt construction: N/A — not applicable

## QA Results

**QA Run: 2026-05-31 — First-time verification (functional-test workflow)**
**Verifier: qa-mod-autocleanup**
**Method: Live server testing against PostgreSQL — curl against real endpoints + DB state inspection**

### Environment

- Docker Compose: postgres:16 (healthy), redis:7.2-alpine (healthy)
- Server: Spring Boot 3.3.5 on port 8080, started with .env variables
- Quartz: JDBC job store confirmed active (qrtz_triggers table, PostgreSQL-backed)
- Unit tests: 26/26 pass (AutoCleanupServiceTest: 17, AutoCleanupControllerTest: 6, AutoCleanupJobTest: 3)

### AC Verification Results

| AC | Description | Result | Evidence |
|----|-------------|--------|----------|
| AC-033 | Create staleness reminder with correct label for non-pinned, non-archived stale items | PASS | Job run created PENDING reminders for items with last_visited_at older than 30-day threshold. Label confirmed: "You haven't revisited this in 30 days — still need it?" (em dash U+2014 verified). Pinned item and archived item got no reminder. Never-visited item falls back to created_at via COALESCE. |
| AC-034 | Auto-archive item 7 days after dismissed reminder if not visited | PASS | Item with DISMISSED reminder (updated_at = 8 days ago) and no post-dismissal visit was auto-archived. Item with DISMISSED reminder but visited 3 days ago was NOT archived. Log: "Item auto-archived: staleness reminder dismissed without visit itemId=47 userId=18 reminderDismissedAt=2026-05-23T17:54:26.926064Z". |
| AC-035 | Clear pending staleness reminder and update last_visited_at on URL open | PASS | POST /api/items/41/visit returned HTTP 204. PENDING reminder for item 41 deleted (0 remaining after visit). last_visited_at updated from 2026-04-16 to 2026-05-31 (current timestamp). |
| AC-036 | NOT update last_visited_at on scroll past in list view | PASS | GET /api/items list endpoint returned item 45 with last_visited_at=null; DB confirmed last_visited_at remained null after list call. Only POST /api/items/{id}/visit calls recordVisit. |
| AC-037 | Not create staleness reminders for pinned items | PASS | Item 42 (is_pinned=TRUE, stale 45 days) received no reminder after multiple job runs. findStaleItemsForUser query enforces is_pinned = FALSE. |
| AC-038 | Apply updated threshold (14/30/60/90) on next job run; reject other values with HTTP 400 | PASS | PUT /api/cleanup-settings with 14, 30, 60, 90 returned HTTP 200 with updated threshold. PUT with 7, 0, 100, 45 returned HTTP 400 {"error":{"code":"INVALID_STALENESS_THRESHOLD","message":"Invalid staleness threshold: 7. Allowed values are 14, 30, 60, or 90 days.","field":"stalenessThresholdDays"}}. Partial update tested: updating autoCleanupEnabled does not reset stalenessThresholdDays. |
| AC-039 | No staleness reminders and no auto-archiving for opted-out users | PASS | User 19 opted out via PUT {"autoCleanupEnabled": false}. User 19 has stale item 46. Job ran for 19 users (userCount=19 in log). DB confirmed 0 reminders for user 19. No "Auto-cleanup processed userId=19" log line at INFO level (correctly suppressed to DEBUG). |
| AC-066 | No duplicate reminder when PENDING or PENDING_CONFIRMATION already exists | PASS | Second job run: user 18 already had PENDING reminders; job logged remindersCreated=0. DB confirmed count stayed at 2. Item 49 with PENDING_CONFIRMATION reminder received no new PENDING reminder after job run. |

### Error Envelope Format

Error responses conform to production.md Shared Convention:
```json
{
  "error": {
    "code": "INVALID_STALENESS_THRESHOLD",
    "message": "Invalid staleness threshold: 7. Allowed values are 14, 30, 60, or 90 days.",
    "field": "stalenessThresholdDays"
  }
}
```

### Shared Convention Checks

| Convention | Check | Result |
|------------|-------|--------|
| Feature-based directory structure | All 10 source files in `backend/src/main/java/com/tabvault/backend/autocleanup/` | PASS |
| Quartz JDBC job store | `spring.quartz.job-store-type=jdbc` in application.properties; qrtz_triggers table verified in PostgreSQL | PASS |
| Error response envelope | `{"error":{"code":"...","message":"...","field":"..."}}` confirmed in live test | PASS |
| No hardcoded secrets | Cron expression from AUTO_CLEANUP_CRON env var | PASS |
| REST error responses use JSON envelope | Confirmed for all tested error cases | PASS |

---

### BUG-001 — Implementation Bug

**Classification:** Implementation bug

**AC affected:** AC-033 (staleness reminder creation), AC-034 (auto-archive)

**Severity:** Low — does not prevent archival; causes a dangling PENDING reminder on the archived item

**Reproducible description:**

Within a single `processUserCleanup` call, `createStalenessReminders` executes before `archiveItemsPassedGracePeriod`. When an item has a DISMISSED staleness reminder whose `updated_at` is older than 7 days (grace period elapsed) AND the item has not been visited since dismissal:

1. `createStalenessReminders` calls `findStaleItemsForUser(userId, cutoff)` — the item is returned because `is_archived=FALSE` at this point in the execution.
2. The idempotency check (`findByItemIdAndStatus` for PENDING and PENDING_CONFIRMATION) finds nothing — the item only has a DISMISSED reminder.
3. A new PENDING staleness reminder is created for the item.
4. `archiveItemsPassedGracePeriod` then runs and archives the item (correct behavior per AC-034).

**Result:** The item is correctly archived, but it also has a new PENDING staleness reminder that can never be acted on (the item is archived). The archived item has reminder status PENDING in the database.

**Exact reproduction:**

```sql
-- Setup: insert item with dismissed reminder dismissed 8+ days ago, not visited since
INSERT INTO items (user_id, url, title, item_type, is_pinned, is_archived, created_at, last_visited_at)
VALUES (18, 'https://test.example.com', 'Test', 'LINK', FALSE, FALSE, NOW() - INTERVAL '90 days', NOW() - INTERVAL '40 days');
-- (get item id, e.g. 47)
INSERT INTO suggested_reminders (item_id, user_id, label, urgency, detected_date, status, created_at, updated_at)
VALUES (47, 18, 'You haven''t revisited this in 30 days — still need it?', 'LOW', NOW()-INTERVAL '10 days', 'DISMISSED', NOW()-INTERVAL '10 days', NOW()-INTERVAL '8 days');
-- Trigger the job
```

**Actual output (server log, 4th job run, Worker-4):**
```
Staleness reminder created itemId=47 userId=18 thresholdDays=30
Item auto-archived: staleness reminder dismissed without visit itemId=47 userId=18 reminderDismissedAt=2026-05-23T17:54:26.926064Z
Auto-cleanup processed userId=18 remindersCreated=1 itemsArchived=1
```

**Expected output per spec:** The item should be archived. The spec (AC-034) says items should be auto-archived after the grace period; it does not say a new PENDING staleness reminder should be created simultaneously. An item whose dismissal grace period has elapsed should be archived, not reminded again.

**Fix:** Run `archiveItemsPassedGracePeriod` before `createStalenessReminders` within `processUserCleanup`. After archival, archived items are excluded from `findStaleItemsForUser` (which enforces `is_archived = FALSE`), so the just-archived item will not receive a new staleness reminder.

**Alternatively:** Add a check in `createStalenessReminders` to skip items that have a DISMISSED reminder (they are either archived next or have already been handled), but the first fix (reorder) is simpler and correct.

---

### Overall QA Result

**FAIL** — 1 implementation bug found (BUG-001).

7 of 8 acceptance criteria pass cleanly in live testing. BUG-001 affects the interaction between AC-033 and AC-034: items eligible for archival also receive a spurious PENDING staleness reminder in the same job run. Archival itself is correct. The dangling reminder cannot be acted on (item is archived).
