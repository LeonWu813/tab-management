-- V7: Add content analysis result columns to items table
-- These columns are populated by the content analysis pipeline (MOD-003)
-- after the Claude API returns the analysis result.
-- Both columns are nullable: NULL until analysis completes or if analysis fails.

ALTER TABLE items
    ADD COLUMN suggested_category VARCHAR(100),
    ADD COLUMN content_type       VARCHAR(50);
