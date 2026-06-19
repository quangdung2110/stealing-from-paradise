#!/bin/bash

# PostgreSQL init script — chạy trong Docker postgres container
# Tạo databases theo tên từ .env file

echo "Initializing PostgreSQL databases..."

# Identity Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'fs_identity_prod'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE fs_identity_prod OWNER postgres"

# Order Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'fs_order_prod'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE fs_order_prod OWNER postgres"

# Payment Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'fs_payment_prod'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE fs_payment_prod OWNER postgres"

# Flash Sale Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'fs_flashsale_prod'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE fs_flashsale_prod OWNER postgres"

# Worker Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'fs_worker_prod'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE fs_worker_prod OWNER postgres"

echo "PostgreSQL initialization completed!"

