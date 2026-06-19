# Functional Requirements: Authentication & Identity
Service: identity-service
Document ID: FR-IDENTITY

| ID | Requirement | Priority | Related BRs | Related UCs |
|----|------------|----------|-------------|-------------|
| FR-IDENTITY-001 | The system SHALL allow public users to register a new account with username, email, phone, password, and full_name via POST /auth/register | HIGH | BR-IDENTITY-001, BR-IDENTITY-002 | UC-IDENTITY-001 |
| FR-IDENTITY-002 | The system SHALL hash passwords using bcrypt before storage and SHALL NOT store plaintext passwords | HIGH | BR-IDENTITY-002, BR-IDENTITY-010 | UC-IDENTITY-001 |
| FR-IDENTITY-003 | The system SHALL authenticate users via credential (username/email/phone) + password and return JWT access_token (15 min) + refresh_token (7 days) via POST /auth/login | HIGH | BR-IDENTITY-004 | UC-IDENTITY-002 |
| FR-IDENTITY-004 | The system SHALL allow token refresh via POST /auth/refresh using a valid refresh_token, returning a new access_token and rotated refresh_token | HIGH | BR-IDENTITY-004 | UC-IDENTITY-002 |
| FR-IDENTITY-005 | The system SHALL revoke access tokens on logout via POST /auth/logout by adding JTI to Redis blocklist with TTL = remaining token lifetime | HIGH | BR-IDENTITY-004 | UC-IDENTITY-002 |
| FR-IDENTITY-006 | The system SHALL return the authenticated user profile via GET /users/me, including user_id, username, email, phone, full_name, status, created_at | HIGH | -- | UC-IDENTITY-003 |
| FR-IDENTITY-007 | The system SHALL allow the authenticated user to update full_name and/or phone via PUT /users/me, enforcing phone uniqueness (BR-IDENTITY-007) | MEDIUM | BR-IDENTITY-007 | UC-IDENTITY-003 |
| FR-IDENTITY-008 | The system SHALL allow the authenticated user to change password via POST /users/me/change-password, requiring old_password verification (BR-IDENTITY-010) | MEDIUM | BR-IDENTITY-010 | UC-IDENTITY-003 |
| FR-IDENTITY-009 | The system SHALL provide CRUD operations for user addresses via GET/POST/PUT/DELETE /users/me/addresses, with default-address enforcement (BR-IDENTITY-006) | MEDIUM | BR-IDENTITY-006 | UC-IDENTITY-004 |
| FR-IDENTITY-010 | The system SHALL allow public registration as a seller via POST /auth/register/seller, creating a user with SELLER role | HIGH | BR-IDENTITY-001, BR-IDENTITY-002, BR-IDENTITY-005 | UC-IDENTITY-006 |
| FR-IDENTITY-011 | The system SHALL allow admin users to list users with filters (role, query) via GET /admin/users | MEDIUM | BR-IDENTITY-008 | -- |
| FR-IDENTITY-014 | The system SHALL publish Kafka events for implemented account lifecycle changes: `account.updated` and `seller.registered`. `account.created` is not part of the current event contract. | MEDIUM | -- | UC-IDENTITY-003, UC-IDENTITY-006 |
| FR-IDENTITY-015 | The system SHALL respond to order.address Kafka request-reply pattern to provide shipping address data to Order Service during checkout | MEDIUM | -- | UC-IDENTITY-004 |
