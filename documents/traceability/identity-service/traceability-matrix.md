# Traceability Matrix: Identity Service
Service: identity-service
Updated: 2026-05-09

## FR -> BR Traceability

| Functional Requirement | Business Rules |
|------------------------|----------------|
| FR-IDENTITY-001 (Register) | BR-IDENTITY-001, BR-IDENTITY-002 |
| FR-IDENTITY-002 (Bcrypt hashing) | BR-IDENTITY-002, BR-IDENTITY-010 |
| FR-IDENTITY-003 (Login JWT) | BR-IDENTITY-004 |
| FR-IDENTITY-004 (Token refresh) | BR-IDENTITY-004 |
| FR-IDENTITY-005 (Logout revoke) | BR-IDENTITY-004 |
| FR-IDENTITY-006 (Get profile) | -- |
| FR-IDENTITY-007 (Update profile) | BR-IDENTITY-007 |
| FR-IDENTITY-008 (Change password) | BR-IDENTITY-010 |
| FR-IDENTITY-009 (Address CRUD) | BR-IDENTITY-006 |
| FR-IDENTITY-010 (Seller register) | BR-IDENTITY-001, BR-IDENTITY-002, BR-IDENTITY-005 |
| FR-IDENTITY-011 (Admin list users) | BR-IDENTITY-008 |
| FR-IDENTITY-014 (Kafka events) | -- |
| FR-IDENTITY-015 (Address request-reply) | -- |

## FR -> UC Traceability

| Functional Requirement | Use Cases |
|------------------------|-----------|
| FR-IDENTITY-001 | UC-IDENTITY-001 |
| FR-IDENTITY-002 | UC-IDENTITY-001, UC-IDENTITY-003 |
| FR-IDENTITY-003 | UC-IDENTITY-002 |
| FR-IDENTITY-004 | UC-IDENTITY-002 |
| FR-IDENTITY-005 | UC-IDENTITY-002 |
| FR-IDENTITY-006 | UC-IDENTITY-003 |
| FR-IDENTITY-007 | UC-IDENTITY-003 |
| FR-IDENTITY-008 | UC-IDENTITY-003 |
| FR-IDENTITY-009 | UC-IDENTITY-004 |
| FR-IDENTITY-010 | UC-IDENTITY-006 |
| FR-IDENTITY-011 | -- |
| FR-IDENTITY-014 | UC-IDENTITY-001, UC-IDENTITY-003, UC-IDENTITY-006 |
| FR-IDENTITY-015 | UC-IDENTITY-004 |

## UC -> Entity Traceability

| Use Case | Entities |
|----------|----------|
| UC-IDENTITY-001 (Register) | ENTITY-IDENTITY-001, ENTITY-IDENTITY-002, ENTITY-IDENTITY-003 |
| UC-IDENTITY-002 (Login) | ENTITY-IDENTITY-001 |
| UC-IDENTITY-003 (Manage Profile) | ENTITY-IDENTITY-001 |
| UC-IDENTITY-004 (Manage Addresses) | ENTITY-IDENTITY-006 |
| UC-IDENTITY-006 (Seller Register) | ENTITY-IDENTITY-001, ENTITY-IDENTITY-002, ENTITY-IDENTITY-004 |

## UC -> API Traceability

| Use Case | API Endpoints |
|----------|--------------|
| UC-IDENTITY-001 | POST /auth/register |
| UC-IDENTITY-002 | POST /auth/login, POST /auth/refresh, POST /auth/logout |
| UC-IDENTITY-003 | GET /users/me, PUT /users/me, POST /users/me/change-password |
| UC-IDENTITY-004 | GET /users/me/addresses, POST /users/me/addresses, PUT /users/me/addresses/{id}, DELETE /users/me/addresses/{id} |
| UC-IDENTITY-006 | POST /auth/register/seller |

## API -> Kafka Event Traceability

| API Endpoint | Kafka Event Published |
|-------------|----------------------|
| POST /auth/register | account.created |
| POST /auth/login | account.login |
| PUT /users/me | account.updated |
| POST /auth/register/seller | seller.registered |

## Document Inventory

| Document Type | File | Count |
|--------------|------|-------|
| Entities | entity-user.md, entity-role.md, entity-customer.md, entity-seller.md, entity-admin.md, entity-address.md | 6 |
| Business Rules | br-auth.md | 1 |
| Functional Requirements | fr-auth.md | 1 |
| Use Cases | uc-001-register.md, uc-002-login.md, uc-003-manage-profile.md, uc-004-manage-addresses.md, uc-006-seller-register.md | 5 |
| API Contracts | api-post-auth-register.yaml, api-post-auth-login.yaml, api-get-addresses.yaml | 3 |
| State Diagrams | state-user.md | 1 |
| Traceability | traceability-matrix.md | 1 |
| **Total** | | **18** |
