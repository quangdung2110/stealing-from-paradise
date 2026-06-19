-- V6: Drop tables left over from removed features (2026-06-16).
--   * fs_reminders           : was used by /reminders endpoints (now removed)
--   * token_entry            : Axon framework (never wired in)
--   * saga_entry             : Axon framework (never wired in)
--   * association_value_entry: Axon framework (never wired in)

DROP TABLE IF EXISTS fs_reminders CASCADE;
DROP TABLE IF EXISTS token_entry CASCADE;
DROP TABLE IF EXISTS saga_entry CASCADE;
DROP TABLE IF EXISTS association_value_entry CASCADE;
