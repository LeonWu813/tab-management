# Auto-Cleanup Scheduler Status

## Engineering Progress

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
