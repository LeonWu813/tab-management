-- V4: Create items table
-- Stores saved items (links, notes, videos) for each user.
-- item_type distinguishes link, note, and video types.
-- summary and category_id may be NULL until LLM analysis completes.
-- note_body is plain text; only populated for note items.
-- is_pinned and is_archived track pin/archive state.
-- last_visited_at is updated on click-through.
-- search_vector is a tsvector column maintained by trigger for full-text search.

CREATE TYPE item_type AS ENUM ('LINK', 'NOTE', 'VIDEO');

CREATE TABLE items (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id      BIGINT REFERENCES categories (id) ON DELETE SET NULL,
    item_type        item_type NOT NULL,
    url              TEXT,
    title            VARCHAR(1000),
    favicon_url      TEXT,
    summary          TEXT,
    note_body        TEXT,
    is_pinned        BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived      BOOLEAN NOT NULL DEFAULT FALSE,
    last_visited_at  TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    search_vector    TSVECTOR
);

CREATE INDEX idx_items_user_id ON items (user_id);
CREATE INDEX idx_items_category_id ON items (category_id);
CREATE INDEX idx_items_user_url ON items (user_id, url) WHERE url IS NOT NULL AND is_archived = FALSE;
CREATE INDEX idx_items_search_vector ON items USING GIN (search_vector);

-- Trigger function: maintains search_vector from title, summary, and note_body
-- using the english text search configuration.
CREATE OR REPLACE FUNCTION items_search_vector_update()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.summary, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.note_body, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_items_search_vector_update
BEFORE INSERT OR UPDATE ON items
FOR EACH ROW EXECUTE FUNCTION items_search_vector_update();
