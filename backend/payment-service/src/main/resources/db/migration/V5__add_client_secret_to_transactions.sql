-- Store Stripe PaymentIntent client_secret for frontend payment confirmation
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS client_secret VARCHAR(500);
