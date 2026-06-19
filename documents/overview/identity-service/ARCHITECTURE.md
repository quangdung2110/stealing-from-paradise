# Identity Service — Architecture Overview

> Service: identity-service (SVC-003, Port 8081)
> Database: PostgreSQL
> Source: Backend code `com.flashsale.identityservice`
> Generated: 2026-05-10

---

## Responsibility
Authentication, authorization, user management, address management, and internal user lookups for other services.

## Tech Stack
- Java 25, Spring Boot 4.0.4
- Spring Security + JWT RS256
- PostgreSQL via JPA
- Kafka consumer (request-reply for address lookups)

## Key Features
- Multi-role registration (BUYER, SELLER) with domain-based login
- JWT access token + refresh token with token blacklist on logout
- Address CRUD for buyers
- Internal API for inter-service user/role queries
- MinIO presigned URL for avatar upload
- Password change with current password verification

## Controllers

| Controller | Base Path | Auth | Purpose |
|-----------|-----------|------|---------|
| AuthController | `/v1/auth` | Public | Login, register (buyer+seller), refresh, logout |
| UserController | `/v1/users/me` | Authenticated | Profile, avatar, addresses, change password, register as seller |
| AdminController | `/v1/admin` | ADMIN | List and inspect user accounts |
| InternalUserController | `/v1/internal/users` | Internal | User/role lookup for inter-service calls |

## Domain Model

| Entity | Table | Key Fields |
|--------|-------|------------|
| User | users | id, username, email, phone, password_hash, full_name, status |
| Role | roles | id, user_id, role_name |
| Address | addresses | id, user_id, full_address, province_id, district_id |

## Kafka Integration

| Direction | Topic | Purpose |
|-----------|-------|---------|
| Consume | `order.delivered` | Unlock seller posting capability |
| Consume | `order.cancelled` | Audit log |
| Consume | `refund.admin_approved` | Notify customer/seller |
| Consume | `refund.rejected` | Notify buyer |
| Reply | `order.address.response` | Respond to address lookup requests |

## Security
- JWT RS256 signed tokens
- Token blacklist via Redis on logout
- Domain-based role routing (seller.localhost, admin.localhost)
- BCrypt password hashing
- `@PreAuthorize` role-based access control on all endpoints
