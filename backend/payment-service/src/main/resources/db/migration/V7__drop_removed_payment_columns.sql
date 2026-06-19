-- V7: Drop columns removed from payment entities per database-entities.md (2026-05-03)

-- ── transactions ───────────────────────────────────────────────────────────

ALTER TABLE transactions
    DROP COLUMN IF EXISTS method,
    DROP COLUMN IF EXISTS stripe_pi_id,
    DROP COLUMN IF EXISTS application_fee_pct,
    DROP COLUMN IF EXISTS client_secret;

DROP INDEX IF EXISTS idx_transactions_stripe_pi;

-- ── refunds ────────────────────────────────────────────────────────────────

ALTER TABLE refunds
    DROP COLUMN IF EXISTS user_id,
    DROP COLUMN IF EXISTS adjust_amount;

DROP INDEX IF EXISTS idx_refunds_user_id;

-- ── seller_transfers ───────────────────────────────────────────────────────

ALTER TABLE seller_transfers
    DROP COLUMN IF EXISTS fee_amount,
    DROP COLUMN IF EXISTS net_amount,
    DROP COLUMN IF EXISTS seller_name,
    DROP COLUMN IF EXISTS parent_order_id;

DROP INDEX IF EXISTS idx_seller_transfers_parent_order;
