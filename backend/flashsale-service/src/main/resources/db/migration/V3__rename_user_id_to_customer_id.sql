-- V3: Rename user_id to customer_id on fs_reminders per database-entities.md (2026-05-03)
ALTER TABLE fs_reminders RENAME COLUMN user_id TO customer_id;
