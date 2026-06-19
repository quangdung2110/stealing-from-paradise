# UC-IDENTITY-002: User Login
Service: identity-service

| Property | Value |
|----------|-------|
| Use Case ID | UC-IDENTITY-002 |
| Title | User Login with JWT |
| Actor | Guest (unauthenticated user) |
| Precondition | User account exists in USERS table |
| Postcondition | JWT access_token (15 min) + refresh_token (7 days) issued |
| Trigger | User submits login form with credential + password |
| Business Rules | BR-IDENTITY-004 |
| Entities | ENTITY-IDENTITY-001 (User) |
| API | POST /auth/login |
| Kafka Events | account.login |

### Main Flow
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | Guest | Submits credential (username/email/phone) and password | -- |
| 2 | System | Looks up user by credential in USERS table | ENTITY-IDENTITY-001 |
| 3 | System | Verifies password against bcrypt hash | FR-IDENTITY-002 |
| 4 | System | Generates RS256 JWT access_token (15 min) | BR-IDENTITY-004 |
| 5 | System | Generates RS256 JWT refresh_token (7 days) | BR-IDENTITY-004 |
| 6 | System | Publishes account.login to Kafka | -- |
| 7 | System | Returns 200 with tokens + user profile | -- |

### Alternate Flows
| Flow | Condition | Action |
|------|-----------|--------|
| A1 | User not found | Return 401, message: "Invalid credentials" |
| A2 | Wrong password | Return 401, message: "Invalid credentials" |
| A3 | Validation failure | Return 400 |

### Token Lifecycle
| Token | Lifetime | Rotation |
|-------|----------|----------|
| access_token | 15 minutes | Refreshed via POST /auth/refresh |
| refresh_token | 7 days | Rotated on each refresh |

### Postcondition Details
- access_token contains: user_id, username, role, exp, iat
- refresh_token stored for rotation tracking
