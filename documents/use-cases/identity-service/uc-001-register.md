# UC-IDENTITY-001: Customer Registration
Service: identity-service

| Property | Value |
|----------|-------|
| Use Case ID | UC-IDENTITY-001 |
| Title | Register New Customer Account |
| Actor | Guest (unauthenticated user) |
| Precondition | User is NOT logged in |
| Postcondition | New User (role=BUYER, status=ACTIVE) created; CUSTOMERS row created; ROLE row (BUYER) created |
| Trigger | User submits registration form |
| Business Rules | BR-IDENTITY-001, BR-IDENTITY-002 |
| Entities | ENTITY-IDENTITY-001 (User), ENTITY-IDENTITY-002 (Role), ENTITY-IDENTITY-003 (Customer) |
| API | POST /auth/register |
| Kafka Events | account.created |

### Main Flow
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | Guest | Submits username, email, phone, password, full_name | BR-IDENTITY-002: password >= 8 chars |
| 2 | System | Validates all fields per FR-IDENTITY-001 | Field rules from 02_API_identity_service.md |
| 3 | System | Checks uniqueness of username, email, phone | BR-IDENTITY-001: IF exists -> 409 |
| 4 | System | Hashes password with bcrypt | FR-IDENTITY-002 |
| 5 | System | Inserts USERS row (role=BUYER, status=ACTIVE) | ENTITY-IDENTITY-001 |
| 6 | System | Inserts ROLES row (role_name=BUYER) | ENTITY-IDENTITY-002 |
| 7 | System | Inserts CUSTOMERS row | ENTITY-IDENTITY-003 |
| 8 | System | Publishes account.created to Kafka | FR-IDENTITY-014 |
| 9 | System | Returns 201 with user profile | -- |

### Alternate Flows
| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Username already exists | Return 409, message: "username already exists" |
| A2 | Email already exists | Return 409, message: "email already exists" |
| A3 | Phone already exists | Return 409, message: "phone already exists" |
| A4 | Password too short | Return 400, message: validation error |
| A5 | Invalid email format | Return 400, message: validation error |

### Error Responses
| Status | Condition | BR |
|--------|-----------|-----|
| 400 | Validation failure | BR-IDENTITY-002 |
| 409 | Duplicate username/email/phone | BR-IDENTITY-001 |
