-- V14: Add seller_name back to orders (was dropped in V7, needed for frontend display)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS seller_name VARCHAR(255);
