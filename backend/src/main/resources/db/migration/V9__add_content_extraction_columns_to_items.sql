-- V9: Add content extraction columns to items table
-- These columns are populated by the content extraction pipeline (MOD-004).
-- page_text: extracted readable text (from article HTML, PDF, or YouTube transcript).
--            Used as input to the LLM summarization pipeline (MOD-003).
-- thumbnail_url: thumbnail for video items (YouTube oEmbed URL or og:image).
-- platform: platform identifier for video items ('youtube', 'instagram', 'tiktok').
--           NULL for article and PDF items.
-- All columns are nullable: NULL until extraction completes or if extraction is not applicable.

ALTER TABLE items
    ADD COLUMN page_text      TEXT,
    ADD COLUMN thumbnail_url  TEXT,
    ADD COLUMN platform       VARCHAR(50);
