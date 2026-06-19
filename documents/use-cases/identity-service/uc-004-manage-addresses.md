# UC-IDENTITY-004: Manage Addresses
Service: identity-service

| Property | Value |
|----------|-------|
| Use Case ID | UC-IDENTITY-004 |
| Title | CRUD Address Management |
| Actor | Authenticated User (any role) |
| Precondition | Valid JWT access_token |
| Postcondition | Address created, updated, deleted, or set as default |
| Trigger | User manages shipping addresses in profile |
| Business Rules | BR-IDENTITY-006 |
| Entities | ENTITY-IDENTITY-006 (Address) |
| APIs | GET /users/me/addresses, POST /users/me/addresses, PUT /users/me/addresses/{id}, DELETE /users/me/addresses/{id} |
| Kafka Events | (none directly; responded via order.address request-reply) |

### Main Flow: List Addresses
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | User | Requests address list | JWT validated |
| 2 | System | Queries ADDRESSES WHERE user_id = JWT.user_id | ENTITY-IDENTITY-006 |
| 3 | System | Sorts: is_default=true first | -- |
| 4 | System | Returns 200 with address array | FR-IDENTITY-009 |

### Main Flow: Create Address
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | User | Submits province_id, district_id, full_address, is_default | -- |
| 2 | System | Validates all required fields | -- |
| 3 | System | IF is_default=true THEN set all other addresses is_default=false | -- |
| 4 | System | Inserts ADDRESSES row | ENTITY-IDENTITY-006 |
| 5 | System | Returns 201 with created address | FR-IDENTITY-009 |

### Main Flow: Update Address
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | User | Submits updated fields for address | All fields optional |
| 2 | System | Validates address belongs to user | IF not owner -> 404 |
| 3 | System | IF is_default=true THEN unset other defaults | -- |
| 4 | System | Updates ADDRESSES row | ENTITY-IDENTITY-006 |
| 5 | System | Returns 200 with updated address | FR-IDENTITY-009 |

### Main Flow: Delete Address
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | User | Requests deletion of address | -- |
| 2 | System | Validates address belongs to user | IF not owner -> 404 |
| 3 | System | Checks is_default + count | BR-IDENTITY-006: IF only default -> 400 |
| 4 | System | Deletes ADDRESSES row | ENTITY-IDENTITY-006 |
| 5 | System | Returns 200 | FR-IDENTITY-009 |

### Alternate Flows
| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Address not found | Return 404 |
| A2 | Address belongs to different user | Return 404 |
| A3 | Deleting only default address | Return 400 (BR-IDENTITY-006) |

### Cross-Service Integration
| Consumer | Mechanism | Purpose |
|----------|-----------|---------|
| Order Service | Kafka request-reply `order.address` | Fetch shipping address during checkout (FR-IDENTITY-015) |
