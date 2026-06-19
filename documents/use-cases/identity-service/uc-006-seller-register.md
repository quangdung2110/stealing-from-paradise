# UC-IDENTITY-006: Seller Registration
Service: identity-service

| Property | Value |
|----------|-------|
| Use Case ID | UC-IDENTITY-006 |
| Title | Register as Seller |
| Actor | Guest (unauthenticated user) or existing BUYER |
| Precondition | User is NOT logged in (new account), OR user is BUYER adding SELLER role (future flow) |
| Postcondition | New User with role=SELLER created; SELLERS row created; ROLE row (SELLER) created |
| Trigger | User submits seller registration form |
| Business Rules | BR-IDENTITY-001, BR-IDENTITY-002, BR-IDENTITY-005 |
| Entities | ENTITY-IDENTITY-001 (User), ENTITY-IDENTITY-002 (Role), ENTITY-IDENTITY-004 (Seller) |
| API | POST /auth/register/seller |
| Kafka Events | seller.registered |

### Main Flow
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | Guest | Submits username, email, phone, password, full_name | Same as UC-IDENTITY-001 |
| 2 | System | Validates all fields | BR-IDENTITY-002: password >= 8 chars |
| 3 | System | Checks uniqueness of username, email, phone | BR-IDENTITY-001: IF exists -> 409 |
| 4 | System | Hashes password with bcrypt | FR-IDENTITY-002 |
| 5 | System | Inserts USERS row (role=SELLER, status=ACTIVE) | ENTITY-IDENTITY-001 |
| 6 | System | Inserts ROLES row (role_name=SELLER) | ENTITY-IDENTITY-002 |
| 7 | System | Inserts SELLERS row | ENTITY-IDENTITY-004 |
| 8 | System | Publishes seller.registered to Kafka | FR-IDENTITY-014 |
| 9 | System | Returns 201 with user profile including roles: ["SELLER"] | FR-IDENTITY-010 |

### Alternate Flows
| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Duplicate credentials | Return 409 (BR-IDENTITY-001) |
| A2 | Password too short | Return 400 (BR-IDENTITY-002) |
| A3 | User already has SELLER role | Return 409, message: "User is already a seller" |

### Postcondition: Onboarding
| Step | Description |
|------|-------------|
| 1 | Seller must complete Stripe KYC (identity verification) |
| 2 | Stripe issues onboarding URL (valid 24 hours) |
| 3 | After KYC complete: SELLER_STRIPE_ACCOUNTS row created (Payment Service) |
| 4 | Seller can then list products (after admin approval) |

### Related Kafka Events
| Event | Trigger | Consumer |
|-------|---------|----------|
| seller.registered | Seller account created | Notification Service (welcome email) |
| seller.onboarding_completed | Stripe KYC verified | Payment Service |

### Cross-References
| Document | Reference |
|----------|-----------|
| Business Rules | BR-IDENTITY-005 (Seller role requirement) |
| Entity | ENTITY-IDENTITY-004 (Seller) |
| Entity | ENTITY-IDENTITY-002 (Role) |
| Business Doc | 03_BUSINESS.md -- Seller role requirements (Stripe KYC mandatory) |
