-- V7: Align orders schema with database-entities.md (2026-05-03)
-- Rename user_id -> customer_id, drop columns no longer in spec.

-- ── parent_orders ──────────────────────────────────────────────────────────

ALTER TABLE parent_orders RENAME COLUMN user_id TO customer_id;

ALTER TABLE parent_orders
    DROP COLUMN IF EXISTS loyalty_discount,
    DROP COLUMN IF EXISTS order_code,
    DROP COLUMN IF EXISTS loyalty_points_used,
    DROP COLUMN IF EXISTS address_id,
    DROP COLUMN IF EXISTS timeout_at,
    DROP COLUMN IF EXISTS version;

DROP INDEX IF EXISTS idx_parent_orders_order_code;

-- ── orders ─────────────────────────────────────────────────────────────────

ALTER TABLE orders RENAME COLUMN user_id TO customer_id;

DROP INDEX IF EXISTS idx_orders_user_id;
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);

ALTER TABLE orders
    DROP COLUMN IF EXISTS carrier,
    DROP COLUMN IF EXISTS cancelled_at,
    DROP COLUMN IF EXISTS delivered_at,
    DROP COLUMN IF EXISTS seller_name,
    DROP COLUMN IF EXISTS return_tracking_number;

-- ── order_items ────────────────────────────────────────────────────────────

ALTER TABLE order_items
    DROP COLUMN IF EXISTS variant_name,
    DROP COLUMN IF EXISTS product_id;
