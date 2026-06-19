-- V7: Add registration_deadline to fs_sessions (2026-06-16).
-- Computed as startTime - registrationWindowMinutes at create time (default 30').
-- Sellers can register items only while now() < registration_deadline
-- AND session.status = 'UPCOMING'.

ALTER TABLE fs_sessions
    ADD COLUMN IF NOT EXISTS registration_deadline TIMESTAMP;

-- Backfill existing sessions: deadline = start_time - 30 minutes
UPDATE fs_sessions
SET registration_deadline = start_time - INTERVAL '30 minutes'
WHERE registration_deadline IS NULL;

ALTER TABLE fs_sessions
    ALTER COLUMN registration_deadline SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_fs_sessions_reg_deadline
    ON fs_sessions(registration_deadline);
