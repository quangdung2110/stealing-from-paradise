-- V7: Drop columns removed from the Users entity per database-entities.md (2026-05-03)
-- These columns are no longer used by the application.

ALTER TABLE users
    DROP COLUMN IF EXISTS avatar_url,
    DROP COLUMN IF EXISTS trust_score,
    DROP COLUMN IF EXISTS locked_until,
    DROP COLUMN IF EXISTS lock_reason,
    DROP COLUMN IF EXISTS appeal_count,
    DROP COLUMN IF EXISTS product_posting_suspended,
    DROP COLUMN IF EXISTS last_cancellation_penalty_at,
    DROP COLUMN IF EXISTS last_warning_at,
    DROP COLUMN IF EXISTS last_posting_suspension_at,
    DROP COLUMN IF EXISTS reward_10_orders_accumulated;
