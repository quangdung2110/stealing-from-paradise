# UC-REFUND-002: Approve Refund (Admin)

**Stable ID:** UC-REFUND-002
**Domain**: Refund Service  
**Actor**: Admin  
**Priority**: High  
**References**: [KAFKA_EVENTS.md](../../messaging/refund-service/KAFKA_EVENTS.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Admin is authenticated (JWT with ADMIN role) |
| P2 | REFUND exists with status = PENDING |
| P3 | Admin has reviewed evidence images and refund reason |

---

## Main Flow

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Admin | Reviews refund: evidence images, reason, order details |
| 2 | Admin | Calls POST `/v1/admin/refunds/{refundId}/approve` with optional `return_tracking_number` and `admin_note` |
| 3 | System | Validates refund is in PENDING status (→ [ENTITY-REFUND-001](../../data-models/refund-service/entity-refund.md)) |
| 4 | System | Sets `reviewed_by = admin.id`, `reviewed_at = NOW()`, `admin_note` (→ [ENTITY-REFUND-001](../../data-models/refund-service/entity-refund.md)) |
| 5 | System | Checks SELLER_TRANSFERS for associated transfer (→ ENTITY-PAYMENT-003) |
| 5a | System | IF pre-payout: calls Stripe `Refund.create()` from platform balance |
| 5b | System | IF post-payout: calls Stripe Transfer reversal API |
| 6 | System | On Stripe success: REFUNDS.status = SUCCESS, `refund_ref = re_xxx` (→ [ENTITY-REFUND-001](../../data-models/refund-service/entity-refund.md)) |
| 7 | System | Updates SELLER_TRANSFERS: REFUNDED (pre-payout) or REVERSED (post-payout) (→ ENTITY-PAYMENT-003) |
| 8 | System | Publishes Kafka `refund.admin_approved` |
| 9 | Notification | Notifies buyer (refund approved) and seller |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Refund not PENDING | Return 409 "Refund already processed" |
| A2 | Stripe refund API error | Set REFUNDS.status = FAILED; log error; notify admin |
| A3 | Partial refund approved | Only refund specified items; remaining items stay PENDING (reviewed separately) |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | REFUNDS.status = SUCCESS |
| Q2 | `refund_ref` populated with Stripe refund ID |
| Q3 | SELLER_TRANSFERS updated (REFUNDED or REVERSED) |
| Q4 | `refund.admin_approved` published to Kafka |
| Q5 | Buyer receives refund notification |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-REFUND-003 | Admin approval gate required |
| BR-REFUND-004 | Pre-payout vs post-payout refund execution |
| BR-REFUND-008 | Kafka event publishing |
| BR-REFUND-009 | Return tracking number captures |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| [UC-REFUND-001](uc-001-create-refund.md) | Create Refund (precedes this) |
| [UC-REFUND-003](uc-003-reject-refund.md) | Reject Refund (alternative outcome) |

### Admin listing endpoints

| Endpoint | Usage |
|----------|-------|
| GET `/v1/admin/refunds` | List all refunds (paginated, filterable by status/type/seller/date) |
| GET `/v1/admin/refunds/{refundId}` | Full refund detail with items, evidence, review history, and Stripe ref |

> These admin endpoints support the review workflow in this UC (admin browses pending refunds before approving).
