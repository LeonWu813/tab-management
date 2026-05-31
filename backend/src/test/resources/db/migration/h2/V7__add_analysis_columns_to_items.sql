-- H2-compatible V7: Add content analysis result columns to items table

ALTER TABLE items
    ADD COLUMN suggested_category VARCHAR(100);

ALTER TABLE items
    ADD COLUMN content_type VARCHAR(50);
