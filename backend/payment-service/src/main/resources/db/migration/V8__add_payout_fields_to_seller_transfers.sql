-- V8: Add payout lifecycle fields to seller_transfers
--
-- Business: sellers are NOT paid immediately on payment success.
-- Instead, funds are held until the return window expires (7 days after delivery).
-- These columns support the delayed payout lifecycle:
--   AWAITING_DELIVERY → delivered_at + payout_eligible_at → RETURN_WINDOW → READY_FOR_PAYOUT → PAID_OUT

ALTER TABLE payment.seller_transfers
    ADD COLUMN delivered_at           TIMESTAMP,
    ADD COLUMN payout_eligible_at      TIMESTAMP,
    ADD COLUMN platform_commission_amt DECIMAL(15,2),
    ADD COLUMN payout_at              TIMESTAMP,
    ADD COLUMN payout_retry_count     INTEGER DEFAULT 0;

-- Composite index for the payout cron: find RETURN_WINDOW records whose window has expired
CREATE INDEX IF NOT EXISTS idx_st_payout_eligible
    ON payment.seller_transfers(status, payout_eligible_at);
