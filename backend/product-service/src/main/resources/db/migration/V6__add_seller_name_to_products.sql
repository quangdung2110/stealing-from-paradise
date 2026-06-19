-- =====================================================
-- Flyway Migration: V6__add_seller_name_to_products
-- Description: Adds seller_name column to denormalize seller's display name
--              Updated by SellerInfoConsumer via Kafka (seller.registered,
--              account.updated) so the customer UI can show the real shop
--              name without cross-service HTTP calls.
-- =====================================================

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS seller_name VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_products_seller_name
    ON products (seller_name);

COMMENT ON COLUMN products.seller_name IS 'Display name of the seller (synced from identity-service via Kafka)';
