# Traceability Matrix â€” Order Service

**Document ID:** TRACE-ORDER-001
**Service:** order-service (port 8083)
**Last Updated:** 2026-05-11 (FR-ORDER-019 added; UC-ORDER-008 Seller Cancel re-activated; BR-ORDER-026 + `seller.order_cancelled` added)

---

## Matrix: FR â†’ BR â†’ UC â†’ Entity â†’ API â†’ State

| FR ID | Description | BR IDs | UC IDs | Entity IDs | API Contract | State Transition |
|-------|-------------|--------|--------|------------|-------------|------------------|
| FR-ORDER-001 | Multi-Vendor Checkout | BR-001..009 | UC-ORDER-001 | ENTITY-001,002,003 | product-service api-post-checkout-submit.yaml | -> PENDING |
| FR-ORDER-002 | Checkout Stock Reservation | BR-ORDER-002 | UC-ORDER-001 | ENTITY-003 | â€” | â€” |
| FR-ORDER-003 | Checkout Address Validation | BR-ORDER-003 | UC-ORDER-001 | ENTITY-002 | â€” | â€” |
| FR-ORDER-004 | Order Code Generation | BR-ORDER-006 | â€” | ENTITY-002 | â€” | â€” |
| FR-ORDER-005 | Buyer Order Listing | â€” | UC-ORDER-002 | ENTITY-002 | api-get-orders.yaml | â€” |
| FR-ORDER-006 | Order Detail | â€” | UC-ORDER-002 | ENTITY-002,003 | api-get-orders.yaml | â€” |
| FR-ORDER-007 | Parent Order Detail | â€” | UC-ORDER-002 | ENTITY-001,002 | â€” | â€” |
| FR-ORDER-008 | Buyer Cancel Order | BR-011,021,025 | UC-ORDER-003 | ENTITY-002 | api-post-orders-cancel.yaml | PENDINGâ†’CANCELLED, PAIDâ†’CANCELLED |
| FR-ORDER-019 | Seller Cancel Order (PAID, pre-ship) | BR-011,021,026 | UC-ORDER-008 | ENTITY-002 | api-post-orders-cancel.yaml | PAIDâ†’CANCELLED |
| FR-ORDER-009 | Seller Update Tracking | BR-ORDER-013 | UC-ORDER-004 | ENTITY-002 | api-put-orders-ship.yaml | PAIDâ†’SHIPPING |
| FR-ORDER-010 | Buyer Confirm Delivery | BR-ORDER-014 | UC-ORDER-005 | ENTITY-002 | api-put-orders-ship.yaml | SHIPPINGâ†’DELIVERED |
| FR-ORDER-011 | Auto-Confirm Delivery (JOB-22) | BR-ORDER-015 | â€” | ENTITY-002 | â€” | SHIPPINGâ†’DELIVERED |
| FR-ORDER-012 | Seller RTS | BR-016,022 | UC-ORDER-006 | ENTITY-002 | api-post-orders-return.yaml | SHIPPINGâ†’RETURNED |
| FR-ORDER-013 | Buyer Refund Request | BR-017,018,019 | UC-ORDER-006 | ENTITY-002 | api-post-orders-return.yaml | DELIVEREDâ†’REFUNDED/PARTIALLY_REFUNDED |
| FR-ORDER-014 | Seller Order Listing | â€” | UC-ORDER-007 | ENTITY-002 | api-get-orders.yaml | â€” |
| FR-ORDER-015 | Seller Dashboard | â€” | â€” | ENTITY-002 | api-get-orders.yaml | â€” |
| FR-ORDER-016 | Kafka Event Production | BR-009..016 | â€” | â€” | â€” | ALL |
| FR-ORDER-017 | Kafka Event Consumption | â€” | â€” | â€” | â€” | PENDINGâ†’PAID, etc. |
| FR-ORDER-018 | Saga Orchestration (Axon) | â€” | UC-ORDER-001 | â€” | â€” | Payment lifecycle |

---

## Matrix: Use Case â†’ FR â†’ API â†’ State

| UC ID | Use Case | Actor | FR IDs | API | State Change |
|-------|----------|-------|--------|-----|-------------|
| UC-ORDER-001 | Checkout | BUYER | FR-001,002,003,004 | POST /v1/cart/checkout/submit | â†’ PENDING |
| UC-ORDER-002 | View Orders | BUYER | FR-005,006,007 | GET /orders, /orders/{id}, /orders/parent/{id} | None (read) |
| UC-ORDER-003 | Cancel Order (Buyer) | BUYER | FR-ORDER-008 | POST /orders/{id}/cancel | PENDINGâ†’CANCELLED, PAIDâ†’CANCELLED |
| UC-ORDER-008 | Cancel Order (Seller) | SELLER | FR-ORDER-008,019 | POST /orders/{id}/cancel | PAIDâ†’CANCELLED (tracking_number IS NULL) |
| UC-ORDER-004 | Ship Order | SELLER | FR-ORDER-009 | PUT /orders/{id}/tracking | PAIDâ†’SHIPPING |
| UC-ORDER-005 | Confirm Delivery | BUYER | FR-010,011 | POST /orders/{id}/confirm-received | SHIPPINGâ†’DELIVERED |
| UC-ORDER-006 | Request Return | BUYER/SELLER | FR-012,013 | POST /orders/{id}/return-to-sender, POST /orders/{id}/refunds | SHIPPINGâ†’RETURNED, DELIVEREDâ†’REFUNDED |
| UC-ORDER-007 | View Seller Orders | SELLER | FR-014,015 | GET /sellers/me/orders, GET /sellers/me/dashboard | None (read) |

---

## Matrix: Business Rule â†’ Entity â†’ State

| BR ID | Rule Summary | Entity | State Transition |
|-------|-------------|--------|-----------------|
| BR-ORDER-001 | Cart not empty | ENTITY-003 | â€” |
| BR-ORDER-002 | Stock availability check | ENTITY-003 | â€” |
| BR-ORDER-003 | Address validation | ENTITY-002 | â€” |
| BR-ORDER-004 | Multi-vendor split | ENTITY-001,002 | â†’ PENDING |
| BR-ORDER-005 | Parent financial integrity | ENTITY-001,002 | â€” |
| BR-ORDER-006 | Order code generation | ENTITY-002 | â€” |
| BR-ORDER-007 | Shipping deadline (3 days) | ENTITY-002 | â€” |
| BR-ORDER-008 | Checkout idempotency | ENTITY-001 | â€” |
| BR-ORDER-009 | Kafka event on checkout | â€” | â†’ PENDING |
| BR-ORDER-010 | PENDINGâ†’PAID | ENTITY-002 | PENDINGâ†’PAID |
| BR-ORDER-011 | PENDING/PAIDâ†’CANCELLED (buyer or seller per BR-021) | ENTITY-002 | PENDINGâ†’CANCELLED, PAIDâ†’CANCELLED |
| BR-ORDER-012 | Auto-cancel (JOB-13) | ENTITY-002 | PENDINGâ†’CANCELLED |
| BR-ORDER-013 | PAIDâ†’SHIPPING | ENTITY-002 | PAIDâ†’SHIPPING |
| BR-ORDER-014 | SHIPPINGâ†’DELIVERED (buyer) | ENTITY-002 | SHIPPINGâ†’DELIVERED |
| BR-ORDER-015 | SHIPPINGâ†’DELIVERED (auto) | ENTITY-002 | SHIPPINGâ†’DELIVERED |
| BR-ORDER-016 | SHIPPINGâ†’RETURNED (RTS) | ENTITY-002 | SHIPPINGâ†’RETURNED |
| BR-ORDER-017 | Return window (7 days) | ENTITY-002 | â€” |
| BR-ORDER-018 | DELIVEREDâ†’REFUNDED | ENTITY-002 | DELIVEREDâ†’REFUNDED |
| BR-ORDER-019 | DELIVEREDâ†’PARTIALLY_REFUNDED | ENTITY-002 | DELIVEREDâ†’PARTIALLY_REFUNDED |
| BR-ORDER-020 | RETURNEDâ†’REFUNDED (auto) | ENTITY-002 | RETURNEDâ†’REFUNDED |
| BR-ORDER-021 | Cancellation actor rules (BUYER: PENDING/PAID; SELLER: PAID + tracking_number IS NULL) | ENTITY-002 | â€” |
| BR-ORDER-022 | RTS vs Buyer Refund | ENTITY-002 | â€” |
| BR-ORDER-023 | Parent status sync | ENTITY-001,002 | â€” |
| BR-ORDER-024 | Immutable shipping snapshot | ENTITY-002 | â€” |
| BR-ORDER-025 | Stock reservation release | ENTITY-003 | â€” |
| BR-ORDER-026 | Seller cancel â€” full refund + reason â‰¥10 chars + emit `seller.order_cancelled` | ENTITY-002 | PAIDâ†’CANCELLED |

---

## Matrix: API Contract â†’ Operations

| API Contract File | Method | Path | Auth | UC |
|-------------------|--------|------|------|----|
| api-post-orders-checkout.yaml | POST | /orders/checkout | BUYER | UC-ORDER-001 |
| api-get-orders.yaml | GET | /orders | BUYER | UC-ORDER-002 |
| api-get-orders.yaml | GET | /orders/{id} | BUYER\|SELLER | UC-ORDER-002 |
| api-get-orders.yaml | GET | /orders/parent/{id} | BUYER | UC-ORDER-002 |
| api-get-orders.yaml | GET | /sellers/me/orders | SELLER | UC-ORDER-007 |
| api-get-orders.yaml | GET | /sellers/me/dashboard | SELLER | UC-ORDER-007 |
| api-put-orders-ship.yaml | PUT | /orders/{id}/tracking | SELLER | UC-ORDER-004 |
| api-put-orders-ship.yaml | POST | /orders/{id}/confirm-received | BUYER | UC-ORDER-005 |
| api-post-orders-cancel.yaml | POST | /orders/{id}/cancel | BUYER | UC-ORDER-003 |
| api-post-orders-cancel.yaml | POST | /orders/{id}/cancel | SELLER | UC-ORDER-008 |
| api-post-orders-return.yaml | POST | /orders/{id}/return-to-sender | SELLER | UC-ORDER-006 |
| api-post-orders-return.yaml | POST | /orders/{id}/refunds | BUYER | UC-ORDER-006 |
| api-post-orders-return.yaml | POST | /orders/parent/{id}/refund | BUYER | UC-ORDER-006 |

---

## Matrix: Kafka Events â†’ Producers / Consumers

| Event | Producer | Consumers | Related State |
|-------|----------|-----------|---------------|
| `order.created` | order-service | product-service, search-service | â†’ PENDING |
| `order.paid` | order-service | â€” | PENDINGâ†’PAID |
| `order.shipped` | order-service | notification-service | PAIDâ†’SHIPPING |
| `order.delivered` | order-service | payment-service, notification-service | SHIPPINGâ†’DELIVERED |
| `order.cancelled` | order-service | product-service (release stock), identity-service (audit), notification-service | PENDINGâ†’CANCELLED, PAIDâ†’CANCELLED |
| `seller.order_cancelled` | order-service (only when cancelled_by=SELLER) | payment-service (auto-refund), notification-service (buyer apology), product-service (idempotent stock release) | PAIDâ†’CANCELLED |
| `order.returned` | order-service | payment-service, product-service, notification-service | SHIPPINGâ†’RETURNED |
| `order.auto_cancelled` | order-service (JOB-13/Axon deadline) | product-service, payment-service, notification-service | PENDINGâ†’CANCELLED |
| `payment.requested` | order-service | payment-service | payment orchestration |
| `payment.success` | payment-service | order-service | â†’ PAID |
| `payment.failed` | payment-service | order-service | (retry/stay PENDING) |
| `refund.rts_completed` | refund-service | order-service | RETURNEDâ†’REFUNDED |
| `refund.admin_approved` | refund-service | order-service | â†’ REFUNDED/PARTIALLY_REFUNDED |

---

## Use Cases & Events to Business Flows

| Use Case / Event | Business Flow | Integration Role |
|------------------|---------------|------------------|
| [UC-ORDER-003](../../use-cases/order-service/uc-003-cancel-order.md) | [flow-order-cancellation](../../flows/cross-service/flow-order-cancellation.md) | Initiates manual order cancellation |
| [UC-ORDER-006](../../use-cases/order-service/uc-006-request-return.md) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) | Initiates buyer partial/full refund request |
| `order.cancelled` | [flow-order-cancellation](../../flows/cross-service/flow-order-cancellation.md) | Published to trigger downstream stock/payment releases |
| `order.returned` | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) (RTS) | Published to trigger auto-refund creation |
| `refund.rts_completed` (consumed) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) (RTS) | Consumed to update order status to REFUNDED |
| `refund.admin_approved` (consumed) | [flow-refund-processing](../../flows/cross-service/flow-refund-processing.md) | Consumed to mark order items as REFUNDED |

---

## Document Inventory

| Category | Files | Count |
|----------|-------|-------|
| Entity | entity-parent-order.md, entity-order.md, entity-order-item.md | 3 |
| Business Rules | br-checkout.md, br-order-lifecycle.md | 2 |
| Functional Requirements | fr-order.md | 1 |
| Use Cases | uc-001..008 | 8 |
| API Contracts | api-post-orders-checkout.yaml, api-get-orders.yaml, api-get-orders-detail.yaml, api-get-orders-parent.yaml, api-get-orders-parent-refund-status.yaml, api-get-orders-refunds.yaml, api-get-orders-refunds-detail.yaml, api-get-orders-refunds-list.yaml, api-get-orders-refunds-presigned-url.yaml, api-get-seller-order-detail.yaml, api-get-sellers-dashboard.yaml, api-get-sellers-orders.yaml, api-post-orders-cancel.yaml, api-post-orders-confirm-received.yaml, api-post-orders-parent-refund.yaml, api-post-orders-parent-refunds-partial.yaml, api-post-orders-refund.yaml, api-post-orders-return.yaml, api-put-orders-ship.yaml | 19 |
| State Diagrams | state-order.md | 1 |
| Traceability | traceability-matrix.md | 1 |
| **Total** | | **35** |

---

## Cross-References

- **Entities:** [data-models/order-service/](../data-models/order-service/)
- **Business Rules:** [business-rules/order-service/](../business-rules/order-service/)
- **Functional Requirements:** [srs/fr/order-service/](../srs/fr/order-service/)
- **Use Cases:** [use-cases/order-service/](../use-cases/order-service/)
- **API Contracts:** [api-contracts/order-service/](../api-contracts/order-service/)
- **State Diagrams:** [state-diagrams/order-service/](../state-diagrams/order-service/)
