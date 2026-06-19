-- =====================================================
-- Flyway Migration: V2__add_published_at_to_products
-- Description: Adds published_at column to track first publish time
-- =====================================================

ALTER TABLE products ADD COLUMN published_at TIMESTAMP;

COMMENT ON COLUMN products.published_at IS 'Timestamp when product was first published (ACTIVE status)';
