# UC-IDENTITY-003: Manage Profile
Service: identity-service

| Property | Value |
|----------|-------|
| Use Case ID | UC-IDENTITY-003 |
| Title | View and Update User Profile |
| Actor | Authenticated User (any role) |
| Precondition | Valid JWT access_token |
| Postcondition | Profile data returned or updated |
| Trigger | User navigates to profile page or submits profile edit form |
| Business Rules | BR-IDENTITY-007, BR-IDENTITY-010 |
| Entities | ENTITY-IDENTITY-001 (User) |
| APIs | GET /users/me, PUT /users/me, POST /users/me/change-password, GET /users/me/avatar/presigned-url |
| Kafka Events | account.updated (on PUT) |

### Main Flow: View Profile
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | User | Requests own profile | JWT validated |
| 2 | System | Extracts user_id from JWT | -- |
| 3 | System | Queries USERS table by user_id | ENTITY-IDENTITY-001 |
| 4 | System | Returns 200 with user profile | FR-IDENTITY-006 |

### Main Flow: Update Profile
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | User | Submits updated full_name and/or phone | All fields optional |
| 2 | System | Validates fields | full_name: 2-100 chars |
| 3 | System | Checks phone uniqueness | BR-IDENTITY-007: IF exists -> 409 |
| 4 | System | Updates USERS row, increments version | ENTITY-IDENTITY-001 |
| 5 | System | Publishes account.updated to Kafka | FR-IDENTITY-014 |
| 6 | System | Returns 200 with updated profile | FR-IDENTITY-007 |

> **Avatar upload**: This UC also supports avatar upload via `GET /users/me/avatar/presigned-url`, which returns a MinIO presigned PUT URL (TTL 15 min). After uploading, the avatar CDN URL is set via `PUT /users/me`.

### Main Flow: Change Password
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | User | Submits old_password + new_password | -- |
| 2 | System | Verifies old_password against bcrypt hash | BR-IDENTITY-010: IF mismatch -> 400 |
| 3 | System | Validates new_password complexity | BR-IDENTITY-002 |
| 4 | System | Hashes new_password with bcrypt, updates row | FR-IDENTITY-002 |
| 5 | System | Returns 200 with success message | FR-IDENTITY-008 |

### Alternate Flows
| Flow | Condition | Action |
|------|-----------|--------|
| A1 | JWT expired | Return 401 (BR-IDENTITY-004) |
| A2 | JWT revoked | Return 401 (BR-IDENTITY-004) |
| A3 | Phone already taken | Return 409 (BR-IDENTITY-007) |
| A4 | Old password wrong | Return 400 (BR-IDENTITY-010) |
