# Identity Service Operations

**Service:** identity-service | **Port:** 8081 | **Database:** PostgreSQL (identity_db)

## Overview

JWT-based authentication service. Issues access/refresh tokens, manages user accounts and roles. Role extraction from subdomain (`seller.localhost`, `admin.localhost`).

## Key Database Tables

| Table | Purpose |
|---|---|
| `users` | Core user accounts (email, password_hash, profile fields) |
| `roles` | Role definitions (USER, SELLER, ADMIN) |
| `user_roles` | Many-to-many user-to-role mapping |
| `addresses` | User shipping/billing addresses |
| `auth_tokens` | Active JWT access tokens |
| `refresh_tokens` | Rotated refresh tokens |

## Running Locally

```bash
# Via docker-compose (recommended)
docker-compose up -d identity-service

# Standalone (requires PostgreSQL on localhost:5432)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_HOST` | Yes | `localhost` | PostgreSQL host |
| `DB_PORT` | Yes | `5432` | PostgreSQL port |
| `DB_NAME` | Yes | `identity_db` | Database name |
| `DB_USER` | Yes | — | Database username |
| `DB_PASSWORD` | Yes | — | Database password |
| `JWT_SECRET` | Yes | — | HMAC signing key (min 256-bit) |
| `JWT_EXPIRATION` | No | `3600000` | Access token TTL (ms) |
| `JWT_REFRESH_EXPIRATION` | No | `2592000000` | Refresh token TTL (ms, ~30d) |

## Health Check

```
GET /actuator/health
```

Healthy response: `{"status": "UP", "components": {"db": {"status": "UP"}}}`

## Logging

Logs to stdout via SLF4J. Structured JSON in production. Key loggers:
```
logging.level.com.paradise.identity=DEBUG
```

## Common Operational Tasks

### Assign a Role
```sql
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'user@example.com' AND r.name = 'SELLER';
```

### Revoke All Tokens for a User
```sql
DELETE FROM auth_tokens WHERE user_id = (SELECT id FROM users WHERE email = 'user@example.com');
DELETE FROM refresh_tokens WHERE user_id = (SELECT id FROM users WHERE email = 'user@example.com');
```

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| 401 on all requests | JWT_SECRET mismatch or token expired | Verify secret matches across instances |
| Login always fails | DB connection or invalid credentials | Check application logs and user lookup by email |
| Refresh token rejected | Token rotation — reused old token | Check refresh_tokens table for revocation |
| Role not recognized | Subdomain mismatch or missing user_role | Verify user_roles join; test subdomain |
