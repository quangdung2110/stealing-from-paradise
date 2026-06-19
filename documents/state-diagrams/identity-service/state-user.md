# State Diagram: User Account Lifecycle

**Stable ID:** `STATE-IDENTITY-001`

Service: identity-service
Entity: ENTITY-IDENTITY-001 (User)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : POST /auth/register\nUC-IDENTITY-001\nBR-IDENTITY-001, BR-IDENTITY-002

    note right of ACTIVE
        All operations permitted:
        - Login (UC-IDENTITY-002)
        - Profile management (UC-IDENTITY-003)
        - Address CRUD (UC-IDENTITY-004)
        - Seller registration (UC-IDENTITY-006)
    end note
```

## State Transition Table

| From | To | Trigger | Actor | BR | UC |
|------|----|---------|-------|-----|-----|
| [*] | ACTIVE | POST /auth/register | Guest | BR-IDENTITY-001, BR-IDENTITY-002 | UC-IDENTITY-001 |
| [*] | ACTIVE | POST /auth/register/seller | Guest | BR-IDENTITY-001, BR-IDENTITY-002 | UC-IDENTITY-006 |
