ALTER TABLE refunds ADD COLUMN IF NOT EXISTS user_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_refunds_user_id ON refunds(user_id);
