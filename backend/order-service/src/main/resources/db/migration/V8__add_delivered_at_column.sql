-- V8: Add delivered_at column to orders table for precise 7-day refund window check
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;
