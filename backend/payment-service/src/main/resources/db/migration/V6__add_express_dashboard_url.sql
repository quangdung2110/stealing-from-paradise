-- Add express_dashboard_url column to seller_stripe_accounts table
-- This URL allows sellers to access their Stripe Express Dashboard for identity verification

ALTER TABLE payment.seller_stripe_accounts
ADD COLUMN IF NOT EXISTS express_dashboard_url TEXT;

COMMENT ON COLUMN payment.seller_stripe_accounts.express_dashboard_url
    IS 'URL to Stripe Express Dashboard for seller to complete identity verification';
