-- V13: Add PENDING value to reminder_status enum
-- The auto-cleanup scheduler (MOD-006) creates staleness reminders with status 'PENDING'.
-- Unlike 'PENDING_CONFIRMATION' (used for AI-detected reminders that await user confirmation),
-- 'PENDING' is the initial status for auto-generated staleness reminders.
-- AC-033, AC-066

ALTER TYPE reminder_status ADD VALUE IF NOT EXISTS 'PENDING';
