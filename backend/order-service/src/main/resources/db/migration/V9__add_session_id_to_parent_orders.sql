-- V9: Add session_id column to parent_orders table
ALTER TABLE parent_orders ADD COLUMN IF NOT EXISTS session_id VARCHAR(255);
