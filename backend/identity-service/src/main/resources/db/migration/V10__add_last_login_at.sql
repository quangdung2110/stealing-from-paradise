-- V10: Add last_login_at column to users table since it is mapped in User entity.
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
