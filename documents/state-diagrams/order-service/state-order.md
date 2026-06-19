# STATE-ORDER: Order Service State Diagram

**Stable ID:** STATE-ORDER-001
**Domain:** Order Lifecycle (8 States)
**Last Updated:** 2026-05-10 (transition #14 reactivated with `seller.order_cancelled`)

---

## Full 8-State Lifecycle Diagram

```
                              â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
                              â”‚                                                     â”‚
                              â–¼                                                     â”‚
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”Œâ”€â”€â”€â”€â”€â”€â”    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”          â”‚
â”‚ PENDING â”‚â”€â”€â”€â–¶â”‚ PAID â”‚â”€â”€â”€â–¶â”‚ SHIPPING â”‚â”€â”€â”€â–¶â”‚ DELIVERED â”‚â”€â”€â”€â–¶â”‚ REFUNDED â”‚          â”‚
â””â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”˜    â””â”€â”€â”¬â”€â”€â”€â”˜    â””â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”˜    â””â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”˜    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜          â”‚
     â”‚            â”‚             â”‚                â”‚              â–²                   â”‚
     â”‚            â”‚             â”‚                â”‚              â”‚                   â”‚
     â”‚            â”‚             â–¼                â”‚              â”‚                   â”‚
     â”‚            â”‚       â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”           â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜                   â”‚
     â”‚            â”‚       â”‚ RETURNED â”‚â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜                                  â”‚
     â”‚            â”‚       â””â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”˜           â”‚                                  â”‚
     â”‚            â”‚            â”‚                 â”‚                                  â”‚
     â”‚            â”‚            â–¼                 â–¼                                  â”‚
     â”‚            â”‚       â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”                    â”‚
     â”‚            â”‚       â”‚ REFUNDED â”‚    â”‚ PARTIALLY_REFUNDED â”‚                    â”‚
     â”‚            â”‚       â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜                    â”‚
     â”‚            â”‚                                                                 â”‚
     â–¼            â–¼                                                                 â”‚
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”                                                                       â”‚
â”‚ CANCELLED â”‚ (terminal)                                                            â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜                                                                       â”‚
```

---

## Transition Table (All 8 States)

| # | From | To | Trigger | Actor | UC | BR | Kafka Event |
|---|------|----|---------|-------|----|----|-------------|
| 1 | (none) | PENDING | `order.checkout_submitted` | BUYER | UC-ORDER-001 | BR-ORDER-001..009 | `order.created` |
| 2 | PENDING | PAID | Stripe payment_intent.succeeded webhook â†’ `payment.success` | SYSTEM | UC-ORDER-001 | BR-ORDER-010 | `order.paid` |
| 3 | PENDING | CANCELLED | POST /orders/{id}/cancel | BUYER \| SELLER | UC-ORDER-003 | BR-ORDER-011, BR-ORDER-021 | `order.cancelled` |
| 4 | PENDING | CANCELLED | JOB-13/Axon auto-cancel (payment timeout: 30m / 10m flash sale) | SYSTEM | â€” | BR-ORDER-012 | `order.auto_cancelled` |
| 5 | PAID | SHIPPING | PUT /orders/{id}/tracking | SELLER | UC-ORDER-004 | BR-ORDER-013 | `order.shipped` |
| 6 | SHIPPING | DELIVERED | POST /orders/{id}/confirm-received | BUYER | UC-ORDER-005 | BR-ORDER-014 | `order.delivered` |
| 7 | SHIPPING | DELIVERED | JOB-22 auto-confirm (7 days after shipping, not RTS'd) | SYSTEM | â€” | BR-ORDER-015 | `order.delivered` |
| 8 | SHIPPING | RETURNED | POST /orders/{id}/return-to-sender | SELLER | UC-ORDER-006 | BR-ORDER-016, BR-ORDER-022 | `order.returned` |
| 9 | RETURNED | REFUNDED | Stripe auto-refund completes â†’ `refund.rts_completed` | SYSTEM | UC-ORDER-006 | BR-ORDER-020 | â€” (consumed) |
| 10 | DELIVERED | REFUNDED | Admin approves full refund | ADMIN | UC-ORDER-006 | BR-ORDER-018 | `refund.admin_approved` |
| 11 | DELIVERED | PARTIALLY_REFUNDED | Admin approves partial refund | ADMIN | UC-ORDER-006 | BR-ORDER-019 | `refund.admin_approved` |
| 12 | SHIPPING | REFUNDED | Admin approves full refund (buyer request) | ADMIN | UC-ORDER-006 | BR-ORDER-018 | `refund.admin_approved` |
| 13 | PAID | REFUNDED | Admin approves full refund (buyer request) | ADMIN | UC-ORDER-006 | BR-ORDER-018 | `refund.admin_approved` |
| 14 | PAID | CANCELLED | Seller cancels (out-of-stock / cannot fulfill) before tracking_number set | SELLER | UC-ORDER-008 | BR-ORDER-011, BR-ORDER-021, BR-ORDER-026 | `order.cancelled` + `seller.order_cancelled` |

---

## State Descriptions

| # | State | Description | Entry Events | Exit Events |
|---|-------|-------------|-------------|-------------|
| 1 | **PENDING** | Order created, awaiting payment | `order.created` | `order.paid`, `order.cancelled`, `order.auto_cancelled` |
| 2 | **PAID** | Payment confirmed by Stripe | `payment.success` | `order.shipped`, `order.cancelled`, `refund.admin_approved` |
| 3 | **SHIPPING** | Seller provided tracking number | `order.shipped` | `order.delivered`, `order.returned`, `refund.admin_approved` |
| 4 | **DELIVERED** | Goods received by buyer (or auto-confirmed) | `order.delivered` | `refund.admin_approved` |
| 5 | **RETURNED** | Seller confirmed RTS, auto-refund initiated | `order.returned` | `refund.rts_completed` |
| 6 | **REFUNDED** | Full refund processed | `refund.admin_approved` or `refund.rts_completed` | â€” (terminal) |
| 7 | **PARTIALLY_REFUNDED** | Partial refund processed | `refund.admin_approved` | â€” (terminal) |
| 8 | **CANCELLED** | Order cancelled, stock released | `order.cancelled` or `order.auto_cancelled` | â€” (terminal) |

---

## Parent Order State Transitions

```
PENDING_PAYMENT â”€â”€â”¬â”€â”€â–¶ PAID (all sub-orders PAID or beyond)
                  â”‚
                  â””â”€â”€â–¶ CANCELLED (all sub-orders CANCELLED)
```

| # | From | To | Trigger | Rule |
|---|------|----|---------|------|
| P1 | PENDING_PAYMENT | PAID | All sub-orders are PAID+ | BR-ORDER-003, BR-ORDER-023 |
| P2 | PENDING_PAYMENT | CANCELLED | All sub-orders are CANCELLED | BR-ORDER-003, BR-ORDER-004 |

---

## Forbidden Transitions

The following transitions are explicitly blocked by business rules:

| From | To | Reason | Rule |
|------|----|--------|------|
| PAID | PENDING | Cannot revert payment | BR-ORDER-010 |
| SHIPPING | PENDING | Cannot un-ship | BR-ORDER-013 |
| DELIVERED | SHIPPING | Cannot un-deliver | BR-ORDER-014 |
| CANCELLED | (any) | Terminal state | BR-ORDER-011 |
| REFUNDED | (any) | Terminal state | BR-ORDER-018 |
| PARTIALLY_REFUNDED | (any) | Terminal state | BR-ORDER-019 |
| SHIPPING | CANCELLED | Must RTS instead | BR-ORDER-016 |

---

## Time-Based Transitions

| Trigger | Timeout | From | To | Mechanism |
|---------|---------|------|----|-----------|
| Payment timeout (regular) | 30 min | PENDING | CANCELLED | Axon Deadline + JOB-13 |
| Payment timeout (Flash Sale) | 10 min | PENDING | CANCELLED | Axon Deadline + JOB-13 |
| Auto-delivery | 7 days after shipped_at | SHIPPING | DELIVERED | JOB-22 (not if RTS'd) |
| Stock reservation TTL | 15 min | (reservation) | released | Product Service |
| Return window | 7 days after delivered_at | DELIVERED | (window closes) | return_window_end |

---

## Kafka Events per Transition

```
checkout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â–¶ order.created
                          payment.requested

payment success â”€â”€â”€â”€â”€â”€â”€â–¶ order.paid (consumed payment.success)

cancel (buyer/seller) â”€â–¶ order.cancelled
cancel (auto) â”€â”€â”€â”€â”€â”€â”€â”€â”€â–¶ order.auto_cancelled

ship (tracking) â”€â”€â”€â”€â”€â”€â”€â–¶ order.shipped

confirm delivery â”€â”€â”€â”€â”€â”€â–¶ order.delivered
auto-delivery â”€â”€â”€â”€â”€â”€â”€â”€â”€â–¶ order.delivered (autoDelivered=true)

RTS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â–¶ order.returned

refund (RTS auto) â”€â”€â”€â”€â”€â–¶ (consumed refund.rts_completed)
refund (admin) â”€â”€â”€â”€â”€â”€â”€â”€â–¶ (consumed refund.admin_approved)
```

---

## Cross-References

- **ENTITY-ORDER-001:** [PARENT_ORDERS](../data-models/order-service/entity-parent-order.md)
- **ENTITY-ORDER-002:** [ORDERS](../data-models/order-service/entity-order.md)
- **BR:** [br-checkout.md](../business-rules/order-service/br-checkout.md)
- **BR:** [br-order-lifecycle.md](../business-rules/order-service/br-order-lifecycle.md)
- **FR:** [fr-order.md](../srs/fr/order-service/fr-order.md)
- **UC-ORDER-001..007:** [use-cases/order-service/](../use-cases/order-service/)
- **Traceability:** [traceability-matrix.md](../traceability/order-service/traceability-matrix.md)
