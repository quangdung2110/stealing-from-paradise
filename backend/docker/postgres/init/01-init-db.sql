-- Tự động chạy bởi docker-entrypoint-initdb.d khi data dir trống
-- KHÔNG chạy lại nếu data dir đã có data

\c postgres;

-- Tất cả services PostgreSQL dùng CHUNG 1 database, phân tách bằng schema
CREATE DATABASE flashsale_platform;

GRANT ALL PRIVILEGES ON DATABASE flashsale_platform TO postgres;

-- Tạo schemas cho từng service trong cùng database
\c flashsale_platform;

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS orders;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS flashsale;
CREATE SCHEMA IF NOT EXISTS worker;

GRANT ALL ON SCHEMA identity  TO postgres;
GRANT ALL ON SCHEMA orders    TO postgres;
GRANT ALL ON SCHEMA payment   TO postgres;
GRANT ALL ON SCHEMA flashsale TO postgres;
GRANT ALL ON SCHEMA worker    TO postgres;
