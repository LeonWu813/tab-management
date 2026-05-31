-- H2-compatible V8: Fix enum casing (no-op for H2)
-- H2 V6 uses VARCHAR columns for urgency and status, not PostgreSQL custom enum
-- types, so the case mismatch that affects PostgreSQL does not apply here.
-- This migration is a placeholder so Flyway schema history stays in sync
-- between the PostgreSQL and H2 environments.
--
-- AC-012, AC-013
SELECT 1;
