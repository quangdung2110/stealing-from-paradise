-- Add role column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'BUYER';

-- Create index on role column for faster queries
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

