-- H2-compatible V9: Add content extraction columns to items table

ALTER TABLE items
    ADD COLUMN page_text     CLOB;

ALTER TABLE items
    ADD COLUMN thumbnail_url VARCHAR(2048);

ALTER TABLE items
    ADD COLUMN platform      VARCHAR(50);
