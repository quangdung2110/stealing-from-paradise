-- V6: Add optimistic locking version + sequence for parent_orders
-- Fixes: ObjectOptimisticLockingFailureException on concurrent checkout

-- 1. Sequence so order-code can be generated BEFORE the first persist
CREATE SEQUENCE IF NOT EXISTS seq_parent_orders START WITH 100 INCREMENT BY 1;

-- 2. Add version column for optimistic locking (matches @Version on entity)
ALTER TABLE parent_orders
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
