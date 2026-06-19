# Traceability Matrix: Flash Sale Service

**Service:** flashsale-service (port :8086)
**Date:** 2026-05-09
**Stable ID Prefix:** `TRACE-FLASHSALE`

---

## FR to UC Mapping

| FR ID | FR Description | UC ID | UC Description |
|-------|---------------|-------|----------------|
| FR-FLASHSALE-001 | Admin create flash sale session | UC-FLASHSALE-001 | Admin create session |
| FR-FLASHSALE-002 | Auto-calculate registration deadline | UC-FLASHSALE-001 | Admin create session |
| FR-FLASHSALE-003 | Validate session time constraints | UC-FLASHSALE-001 | Admin create session |
| FR-FLASHSALE-004 | Seller register product in session | UC-FLASHSALE-002 | Seller register product |
| FR-FLASHSALE-005 | System auto-approve registration | UC-FLASHSALE-002 | Seller register product |
| FR-FLASHSALE-006 | Admin update session | UC-FLASHSALE-001 | Admin create session |
| FR-FLASHSALE-007 | System transition session status | UC-FLASHSALE-006 | System end session |
| FR-FLASHSALE-008 | View flash sale sessions | UC-FLASHSALE-003 | View sessions |
| FR-FLASHSALE-010 | Publish Kafka events | UC-FLASHSALE-001, UC-FLASHSALE-002, UC-FLASHSALE-006 | Multiple UCs |

---

## FR to BR Mapping

| FR ID | FR Description | BR ID(s) |
|-------|---------------|----------|
| FR-FLASHSALE-001 | Admin create flash sale session | BR-FLASHSALE-001, BR-FLASHSALE-002, BR-FLASHSALE-003 |
| FR-FLASHSALE-002 | Auto-calculate registration deadline | BR-FLASHSALE-002 |
| FR-FLASHSALE-003 | Validate session time constraints | BR-FLASHSALE-001 |
| FR-FLASHSALE-004 | Seller register product in session | BR-FLASHSALE-002, BR-FLASHSALE-009 |
| FR-FLASHSALE-005 | System auto-approve registration | BR-FLASHSALE-002 |
| FR-FLASHSALE-006 | Admin update session | BR-FLASHSALE-008 |
| FR-FLASHSALE-007 | System transition session status | BR-FLASHSALE-004 |
| FR-FLASHSALE-008 | View flash sale sessions | -- |
| FR-FLASHSALE-010 | Publish Kafka events | -- |

---

## UC to BR Mapping

| UC ID | UC Description | BR ID(s) |
|-------|---------------|----------|
| UC-FLASHSALE-001 | Admin create session | BR-FLASHSALE-001, BR-FLASHSALE-002, BR-FLASHSALE-003, BR-FLASHSALE-009 |
| UC-FLASHSALE-002 | Seller register product | BR-FLASHSALE-002, BR-FLASHSALE-009 |
| UC-FLASHSALE-003 | View sessions | -- |
| UC-FLASHSALE-006 | System end session | BR-FLASHSALE-004 |

---

## Entity to BR Mapping

| Entity ID | Entity | BR ID(s) |
|-----------|--------|----------|
| ENTITY-FLASHSALE-001 | FS_SESSIONS | BR-FLASHSALE-001, BR-FLASHSALE-002, BR-FLASHSALE-003, BR-FLASHSALE-004, BR-FLASHSALE-006 |
| ENTITY-FLASHSALE-002 | FS_ITEMS | BR-FLASHSALE-002, BR-FLASHSALE-009 |

---

## Entity to FR Mapping

| Entity ID | Entity | FR ID(s) |
|-----------|--------|----------|
| ENTITY-FLASHSALE-001 | FS_SESSIONS | FR-FLASHSALE-001, FR-FLASHSALE-002, FR-FLASHSALE-003, FR-FLASHSALE-006, FR-FLASHSALE-007, FR-FLASHSALE-008 |
| ENTITY-FLASHSALE-002 | FS_ITEMS | FR-FLASHSALE-004, FR-FLASHSALE-005 |

---

## API Endpoint to UC Mapping

| Method | Endpoint | UC ID |
|--------|----------|-------|
| POST | /flash-sales | UC-FLASHSALE-001 |
| GET | /flash-sales | UC-FLASHSALE-003 |
| GET | /flash-sales/{id} | UC-FLASHSALE-003 |
| PUT | /flash-sales/{id} | UC-FLASHSALE-001 |
| POST | /flash-sales/{id}/items | UC-FLASHSALE-002 |
| GET | /flash-sales/{id}/items | UC-FLASHSALE-003 |
| GET | /flash-sales/active | UC-FLASHSALE-003 || GET | /flash-sales/active | UC-FLASHSALE-003 |

---

## Kafka Events to UC Mapping

| Kafka Event | Trigger UC | Consumer(s) |
|-------------|-----------|-------------|
| `flash_sale.session_created` | UC-FLASHSALE-001 | Audit log |
| `flash_sale.session_started` | UC-FLASHSALE-006 | Notification Service, Product Service |
| `flash_sale.session_ended` | UC-FLASHSALE-006 | Notification Service, Product Service |
| `flash_sale.item_registered` | UC-FLASHSALE-002 | Notification Service |

---

## State Transitions

| From | To | Trigger UC | Trigger BR |
|------|----|------------|------------|
| [*] | UPCOMING | UC-FLASHSALE-001 | BR-FLASHSALE-001, BR-FLASHSALE-003 |
| UPCOMING | ACTIVE | UC-FLASHSALE-006 | BR-FLASHSALE-004 |
| ACTIVE | ENDED | UC-FLASHSALE-006 | BR-FLASHSALE-004 |
| ENDED | [*] | -- | -- |

---

## Document Inventory

| Document | Path | Stable ID |
|----------|------|-----------|
| Entity: FS_SESSIONS | `data-models/flashsale-service/entity-fs-session.md` | ENTITY-FLASHSALE-001 |
| Entity: FS_ITEMS | `data-models/flashsale-service/entity-fs-item.md` | ENTITY-FLASHSALE-002 |
| Business Rules | `business-rules/flashsale-service/br-flash-sale.md` | BR-FLASHSALE-001 through BR-FLASHSALE-009 |
| Functional Requirements | `srs/fr/flashsale-service/fr-flash-sale.md` | FR-FLASHSALE-001 through FR-FLASHSALE-012 |
| UC: Create Session | `use-cases/flashsale-service/uc-001-create-session.md` | UC-FLASHSALE-001 |
| UC: Register Product | `use-cases/flashsale-service/uc-002-register-product.md` | UC-FLASHSALE-002 |
| UC: View Sessions | `use-cases/flashsale-service/uc-003-view-sessions.md` | UC-FLASHSALE-003 |
| UC: End Session | `use-cases/flashsale-service/uc-006-end-session.md` | UC-FLASHSALE-006 |
| API: POST /flash-sales | `api-contracts/flashsale-service/api-post-flash-sales.yaml` | -- |
| API: GET /flash-sales | `api-contracts/flashsale-service/api-get-flash-sales.yaml` | -- |
| State Diagram | `state-diagrams/flashsale-service/state-fs-session.md` | STATE-FLASHSALE-001 |
| Traceability Matrix | `traceability/flashsale-service/traceability-matrix.md` | TRACE-FLASHSALE |

---

*Generated: 2026-05-09 | All files cross-referenced with stable IDs*
