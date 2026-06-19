# Traceability Matrix: Refund Service

**Stable ID:** TRACE-REFUND-001
**Domain**: Refund Service  
**Version**: v1.0  
**Generated**: 2026-05-12  
**References**: All refund-service micro-documentation

---

## Entity to Business Rules

| Entity ID | Entity Name | Business Rules |
|-----------|-------------|----------------|
| [ENTITY-REFUND-001](../../data-models/refund-service/entity-refund.md) | Refund | BR-REFUND-001, BR-REFUND-002, BR-REFUND-003, BR-REFUND-004, BR-REFUND-005, BR-REFUND-006, BR-REFUND-007, BR-REFUND-008 |
| [ENTITY-REFUND-002](../../data-models/refund-service/entity-refund-item.md) | Refund Item | BR-REFUND-006, BR-REFUND-007, BR-REFUND-009 |

---

## Business Rules to Functional Requirements

| BR ID | Description | FR ID(s) |
|-------|-------------|----------|
| BR-REFUND-001 | Return window eligibility | FR-REFUND-001 |
| BR-REFUND-002 | Evidence requirement | FR-REFUND-002 |
| BR-REFUND-003 | Admin approval gate | FR-REFUND-003 |
| BR-REFUND-004 | Pre-payout vs post-payout refund | FR-REFUND-004 |
| BR-REFUND-005 | RTS auto-refund | FR-REFUND-005 |
| BR-REFUND-006 | Refund amount validation | FR-REFUND-001 |
| BR-REFUND-007 | Refund grouping by UUID | (entity-level) |
| BR-REFUND-008 | Kafka event publishing | FR-REFUND-003, FR-REFUND-004, FR-REFUND-005 |
| BR-REFUND-009 | Return tracking number | FR-REFUND-003 |

---

## Functional Requirements to Use Cases

| FR ID | Description | Use Cases |
|-------|-------------|-----------|
| FR-REFUND-001 | Refund eligibility validation | [UC-REFUND-001](../../use-cases/refund-service/uc-001-create-refund.md) |
| FR-REFUND-002 | Refund evidence upload | [UC-REFUND-001](../../use-cases/refund-service/uc-001-create-refund.md) |
| FR-REFUND-003 | Admin refund review | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md), [UC-REFUND-003](../../use-cases/refund-service/uc-003-reject-refund.md) |
| FR-REFUND-004 | Stripe refund execution | [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) |
| FR-REFUND-005 | RTS auto-refund flow | [UC-REFUND-001](../../use-cases/refund-service/uc-001-create-refund.md) |

---

## Use Case to State Diagram

| Use Case | State Diagrams Affected |
|----------|------------------------|
| [UC-REFUND-001](../../use-cases/refund-service/uc-001-create-refund.md) | [state-refund](../../state-diagrams/refund-service/state-refund.md) ([*] -> PENDING_REVIEW / PROCESSING) |
| [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) | [state-refund](../../state-diagrams/refund-service/state-refund.md) (PENDING_REVIEW -> APPROVED -> PROCESSING -> COMPLETED) |
| [UC-REFUND-003](../../use-cases/refund-service/uc-003-reject-refund.md) | [state-refund](../../state-diagrams/refund-service/state-refund.md) (PENDING_REVIEW -> REJECTED) |

---

## Use Case to API Contracts

| Use Case | API Contract(s) |
|----------|-----------------|
| [UC-REFUND-001](../../use-cases/refund-service/uc-001-create-refund.md) | [api-post-refunds.yaml](../../api-contracts/refund-service/api-post-refunds.yaml) |
| [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) | [api-put-refunds-approve.yaml](../../api-contracts/refund-service/api-put-refunds-approve.yaml), [api-get-admin-refunds.yaml](../../api-contracts/refund-service/api-get-admin-refunds.yaml), [api-get-admin-refunds-detail.yaml](../../api-contracts/refund-service/api-get-admin-refunds-detail.yaml) |
| [UC-REFUND-003](../../use-cases/refund-service/uc-003-reject-refund.md) | [api-post-admin-refunds-reject.yaml](../../api-contracts/refund-service/api-post-admin-refunds-reject.yaml) |

---

## Kafka Event to Entity

| Kafka Topic | Producing Entity | Affected Entities |
|-------------|-----------------|-------------------|
| `refund.requested` | REFUNDS | (notification only) |
| `refund.admin_approved` | REFUNDS | REFUND_ITEMS |
| `refund.rejected` | REFUNDS | (notification only) |
| `refund.rts_completed` | REFUNDS | (notification, order update) |
| `order.returned` (consumed) | -- | REFUNDS, REFUND_ITEMS |

---

## Use Cases & Events to Business Flows

| Use Case / Event | Business Flow | Integration Role |
|------------------|---------------|------------------|
| [UC-REFUND-001](../../use-cases/refund-service/uc-001-create-refund.md) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) | Buyer request ingestion & validation |
| [UC-REFUND-002](../../use-cases/refund-service/uc-002-approve-refund.md) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) | Admin approval, Stripe API refund trigger |
| [UC-REFUND-003](../../use-cases/refund-service/uc-003-reject-refund.md) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) | Admin rejection handling |
| `refund.admin_approved` | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) | Triggers seller transfer reversal and order status updates |
| `order.returned` (consumed) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) (RTS) | Consumed to auto-process refund |

---

## Full Coverage Matrix

```
                    ENTITY  BR    FR    UC    API   STATE  FLOW
ENTITY-REFUND-001     X     X     X     X     X      X      X
ENTITY-REFUND-002     X     X     X     X     X      X      X

BR-REFUND-001..009    -     X     X     X     X      X      X

FR-REFUND-001..005    -     -     X     X     X      X      X

UC-REFUND-001..003    -     -     -     X     X      X      X
```

**Key**: X = coverage exists, - = not applicable
