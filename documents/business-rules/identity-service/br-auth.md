# Business Rules: Authentication & Authorization
Service: identity-service
Document ID: BR-IDENTITY

## BR-IDENTITY-001: Unique Credential Enforcement
| Property | Value |
|----------|-------|
| ID | BR-IDENTITY-001 |
| Statement | IF username OR email OR phone already exists in USERS table THEN reject registration with HTTP 409 |
| Trigger | POST /auth/register, POST /auth/register/seller |
| Entity | ENTITY-IDENTITY-001 (User.username, User.email, User.phone) |
| Error Response | `{ "error": "CONFLICT", "message": "username|email|phone already exists" }` |
| Test Case | Register with existing username/email/phone -> 409 |

## BR-IDENTITY-002: Password Complexity
| Property | Value |
|----------|-------|
| ID | BR-IDENTITY-002 |
| Statement | IF password length < 8 chars OR missing uppercase OR missing digit THEN reject with HTTP 400 |
| Trigger | POST /auth/register, POST /auth/register/seller, POST /users/me/change-password |
| Entity | ENTITY-IDENTITY-001 (User.password) |
| Error Response | `{ "error": "VALIDATION_FAILED", "message": "Password must be at least 8 chars with 1 uppercase and 1 digit" }` |
| Test Case | Register with "abc" -> 400; Register with "abcdefgh" -> 400 (no uppercase/digit) |

## BR-IDENTITY-004: Token Expiration
| Property | Value |
|----------|-------|
| ID | BR-IDENTITY-004 |
| Statement | IF access_token is expired OR revoked THEN return HTTP 401 |
| Trigger | Any JWT-protected endpoint |
| Entity | JWT (access_token) |
| Error Response | `{ "error": "UNAUTHORIZED", "message": "Token expired or invalid" }` |
| Token Lifetimes | access_token: 15 min; refresh_token: 7 days |
| Revocation | Redis blocklist: `revoked_token:{jti} = 1 EX 900` |

## BR-IDENTITY-005: Seller Role Requirement
| Property | Value |
|----------|-------|
| ID | BR-IDENTITY-005 |
| Statement | IF user does not have SELLER role THEN reject seller-specific operations with HTTP 403 |
| Trigger | Seller registration (if already SELLER), seller product listing, flash sale registration |
| Entity | ENTITY-IDENTITY-002 (Role.role_name) |
| Related UC | UC-IDENTITY-006 (Seller Registration) |

## BR-IDENTITY-006: Default Address Deletion Prevention
| Property | Value |
|----------|-------|
| ID | BR-IDENTITY-006 |
| Statement | IF address.is_default = true AND it is the ONLY address THEN reject DELETE with HTTP 400 |
| Trigger | DELETE /users/me/addresses/{addressId} |
| Entity | ENTITY-IDENTITY-006 (Address.is_default) |
| Error Response | `{ "error": "BAD_REQUEST", "message": "Cannot delete the only default address" }` |
| Related UC | UC-IDENTITY-004 (Manage Addresses) |

## BR-IDENTITY-007: Phone Uniqueness on Update
| Property | Value |
|----------|-------|
| ID | BR-IDENTITY-007 |
| Statement | IF phone in PUT /users/me already exists for another user THEN reject with HTTP 409 |
| Trigger | PUT /users/me |
| Entity | ENTITY-IDENTITY-001 (User.phone) |
| Related UC | UC-IDENTITY-003 (Manage Profile) |

## BR-IDENTITY-008: Admin-Only User Management
| Property | Value |
|----------|-------|
| ID | BR-IDENTITY-008 |
| Statement | IF requester role != ADMIN THEN reject /admin/** endpoints with HTTP 403 |
| Trigger | GET /admin/users |
| Entity | ENTITY-IDENTITY-005 (Admin) |

## BR-IDENTITY-010: Old Password Verification on Change
| Property | Value |
|----------|-------|
| ID | BR-IDENTITY-010 |
| Statement | IF old_password does not match current bcrypt hash THEN reject password change with HTTP 400 |
| Trigger | POST /users/me/change-password |
| Entity | ENTITY-IDENTITY-001 (User.password) |
| Related UC | UC-IDENTITY-003 (Manage Profile) |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| STATE-IDENTITY-001 | [state-user.md](../../state-diagrams/identity-service/state-user.md) |
