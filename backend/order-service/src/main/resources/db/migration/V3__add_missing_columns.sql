-- V2: Add missing columns to support full Order Service APIs

-- orders table: add carrier, cancelled_at, delivered_at, seller_name, return_tracking_number
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS carrier               VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cancelled_at          TIMESTAMP,
    ADD COLUMN IF NOT EXISTS delivered_at          TIMESTAMP,
    ADD COLUMN IF NOT EXISTS seller_name           VARCHAR(255),
    ADD COLUMN IF NOT EXISTS return_tracking_number VARCHAR(100);

-- parent_orders table: add order_code, loyalty_points_used, address_id, timeout_at
ALTER TABLE parent_orders
    ADD COLUMN IF NOT EXISTS order_code            VARCHAR(50),
    ADD COLUMN IF NOT EXISTS loyalty_points_used   INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS address_id            BIGINT,
    ADD COLUMN IF NOT EXISTS timeout_at            TIMESTAMP;

-- Backfill order_code for existing parent_orders (PO-YYYYMMDD-{id})
UPDATE parent_orders
SET order_code = 'PO-' || TO_CHAR(created_at, 'YYYYMMDD') || '-' || id
WHERE order_code IS NULL;

ALTER TABLE parent_orders
    ALTER COLUMN order_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_parent_orders_order_code ON parent_orders(order_code);

-- order_items table: add variant_name, product_id
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS variant_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS product_id    VARCHAR(24);
