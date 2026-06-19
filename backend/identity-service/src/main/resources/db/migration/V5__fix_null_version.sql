-- V5__fix_null_version.sql
-- Fix users that have NULL version (causes DataIntegrityViolationException
-- on detached entity with @Version field at startup)
UPDATE identity.users SET version = 0 WHERE version IS NULL;
