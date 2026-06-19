-- Drop loyalty, trust score, appeals, and ban history tables
-- Order: respect FK dependencies (appeals -> trust_score_logs -> trust_score_events_config)
--        point_transactions, loyalty_accounts, user_ban_history are independent

DROP TABLE IF EXISTS appeals CASCADE;
DROP TABLE IF EXISTS point_transactions CASCADE;
DROP TABLE IF EXISTS trust_score_logs CASCADE;
DROP TABLE IF EXISTS loyalty_accounts CASCADE;
DROP TABLE IF EXISTS user_ban_history CASCADE;
DROP TABLE IF EXISTS trust_score_events_config CASCADE;
