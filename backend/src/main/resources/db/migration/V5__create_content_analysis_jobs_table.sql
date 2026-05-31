-- V5: Create content_analysis_jobs outbox table
-- Tracks pending and failed LLM analysis jobs for saved items.
-- Jobs are written here on item save so that analysis survives service restarts.
-- status: PENDING -> PROCESSING -> COMPLETED | FAILED
-- retry_count and last_attempted_at are used by the LLM service for retry logic.

CREATE TYPE job_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');

CREATE TABLE content_analysis_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    item_id             BIGINT NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    status              job_status NOT NULL DEFAULT 'PENDING',
    retry_count         INT NOT NULL DEFAULT 0,
    last_attempted_at   TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_content_analysis_jobs_item_id ON content_analysis_jobs (item_id);
CREATE INDEX idx_content_analysis_jobs_status ON content_analysis_jobs (status);
