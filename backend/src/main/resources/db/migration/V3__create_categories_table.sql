-- V3: Create categories table
-- Stores user-defined item categories.
-- Each user has an implicit "uncategorized" state (category_id IS NULL on items).
-- name is 1–50 characters, color is a 7-character hex code (e.g. #ff5733).

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(50) NOT NULL,
    color       VARCHAR(7) NOT NULL,
    icon        VARCHAR(255),
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_user_id ON categories (user_id);
