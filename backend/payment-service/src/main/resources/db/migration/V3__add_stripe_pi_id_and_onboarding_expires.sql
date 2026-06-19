-- Add stripe_pi_id to transactions for PaymentIntent tracking
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS stripe_pi_id          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS application_fee_pct   DECIMAL(5,2) DEFAULT 5.00;

-- Add onboarding_url_expires_at to seller_stripe_accounts
ALTER TABLE seller_stripe_accounts
    ADD COLUMN IF NOT EXISTS onboarding_url_expires_at TIMESTAMP;

-- Add fee and net amount to seller_transfers
ALTER TABLE seller_transfers
    ADD COLUMN IF NOT EXISTS fee_amount     DECIMAL(15,2),
    ADD COLUMN IF NOT EXISTS net_amount     DECIMAL(15,2),
    ADD COLUMN IF NOT EXISTS seller_name    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS parent_order_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_transactions_stripe_pi        ON transactions(stripe_pi_id);
CREATE INDEX IF NOT EXISTS idx_seller_transfers_parent_order ON seller_transfers(parent_order_id);
