-- V11: Add notification_preferences column to users table since it is mapped in User entity.
ALTER TABLE users ADD COLUMN IF NOT EXISTS notification_preferences JSONB;
