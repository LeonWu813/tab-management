-- H2-compatible V13: Add PENDING reminder status (no-op for H2)
-- H2 uses VARCHAR columns for the status field (not a PostgreSQL custom enum type),
-- so no DDL change is needed. The PENDING value is accepted as-is by VARCHAR.
-- This migration is a placeholder to keep Flyway schema history in sync.
-- AC-033, AC-066
SELECT 1;
