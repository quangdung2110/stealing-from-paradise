-- V9: Re-add avatar_url column to users table since it is used by User entity and frontend Profile page.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);
