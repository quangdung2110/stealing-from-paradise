# FR-ORDER-001 to FR-ORDER-018: Order Service Functional Requirements

**Document ID:** FR-ORDER
**Service:** order-service (port 8083)
**Last Updated:** 2026-05-09

---

## FR-ORDER-001: Multi-Vendor Checkout

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-001

**Description:** System shall consume Product Service checkout submissions and split them into one parent order and N sub-orders, one per seller, in a single atomic database transaction.

**Acceptance Criteria:**
- [x] Consumes `order.checkout_submitted` from Product Service
- [x] Items grouped by seller_id; one sub-order created per seller
- [x] One parent_order created linking all sub-orders
- [x] All INSERTs in a single transaction; rollback on any failure
- [x] Stores shipping address snapshot from the checkout event
- [x] Deprecated direct `POST /orders/checkout` returns 501

**Related:** BR-ORDER-001, BR-ORDER-002, BR-ORDER-003, BR-ORDER-004, BR-ORDER-005
**UC:** UC-ORDER-001
**API:** product-service `api-post-checkout-submit.yaml`; order-service `api-post-orders-checkout.yaml` is deprecated

---

## FR-ORDER-002: Checkout Stock Reservation

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-002

**Description:** Product Service shall validate and reserve stock before emitting the checkout submission consumed by Order Service.

**Acceptance Criteria:**
- [x] `POST /v1/cart/checkout/preview` validates active variants, price, and stock
- [x] `POST /v1/cart/checkout/submit` revalidates stock
- [x] Product Service creates PENDING stock reservations with checkout `session_id`
- [x] Product Service emits `order.checkout_submitted` only after reservation succeeds

**Related:** BR-ORDER-002
**UC:** UC-ORDER-001

---

## FR-ORDER-003: Checkout Address Validation

**Priority:** P1 (High)
**Stable ID:** FR-ORDER-003

**Description:** Product Service shall validate the checkout address with Identity Service before publishing `order.checkout_submitted`.

**Acceptance Criteria:**
- [x] Sends `order.address.request` to Identity Service
- [x] Receives `order.address.response` with full address details
- [x] Rejects with 409 if address invalid or not owned by user
- [x] Snapshots address into checkout event and orders.shipping_address (JSONB)

**Related:** BR-ORDER-003
**UC:** UC-ORDER-001

---

## FR-ORDER-004: Order Code Generation

**Priority:** P1 (High)
**Stable ID:** FR-ORDER-004

**Description:** System shall generate a unique human-readable order_code for each sub-order in format OR-YYYYMMDD-{id}.

**Acceptance Criteria:**
- [ ] Format: OR-YYYYMMDD-{orders.id}
- [ ] Unique across all orders (UNIQUE constraint)
- [ ] Generated at order creation, never changed

**Related:** BR-ORDER-006

---

## FR-ORDER-005: Buyer Order Listing

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-005

**Description:** Buyer shall be able to list their orders with status and date filters and pagination.

**Acceptance Criteria:**
- [ ] GET /orders returns paginated list (page, size, max 100 per page)
- [ ] Filterable by status (enum of 8 states)
- [ ] Filterable by from_date and to_date (ISO 8601)
- [ ] Only returns orders belonging to authenticated buyer

**Related:** UC-ORDER-002
**API:** api-get-orders.yaml

---

## FR-ORDER-006: Order Detail

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-006

**Description:** Buyer or seller shall be able to view full order detail including items, shipping, and refund info.

**Acceptance Criteria:**
- [ ] GET /orders/{id} returns complete order with items[]
- [ ] Buyer can only view own orders
- [ ] Seller can only view own sub-orders
- [ ] Includes refunded_quantity per item
- [ ] Returns 404 if order not found, 403 if not authorized

**Related:** UC-ORDER-002
**API:** api-get-orders.yaml

---

## FR-ORDER-007: Parent Order Detail

**Priority:** P1 (High)
**Stable ID:** FR-ORDER-007

**Description:** Buyer shall be able to view parent order with all sub-orders and payment information.

**Acceptance Criteria:**
- [ ] GET /orders/parent/{parentOrderId} returns parent + all sub-orders
- [ ] Only accessible by the buyer who owns the parent order
- [ ] Includes payment status summary

**Related:** UC-ORDER-002

---

## FR-ORDER-008: Buyer Cancel Order

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-008

**Description:** Buyer shall be able to cancel an order in PENDING or PAID status (PAID cancellation only before seller ships) with a reason.

**Acceptance Criteria:**
- [ ] POST /orders/{id}/cancel accepts reason and optional note
- [ ] Allowed when order.status IN (PENDING, PAID) â€” BUYER can cancel PENDING or PAID; SELLER can cancel PAID only (see BR-ORDER-011, FR-ORDER-019)
- [ ] Sets cancelled_by = BUYER, cancel_reason = provided reason
- [ ] Returns 409 if order not in PENDING or PAID
- [ ] Produces order.cancelled Kafka event
- [ ] Triggers stock release via `variant.stock_updated` (emitted by Product Service)

**Related:** BR-ORDER-011
**UC:** UC-ORDER-003
**API:** api-post-orders-return.yaml (shared cancel/return)

---

## FR-ORDER-009: Seller Update Tracking

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-009

**Description:** Seller shall be able to provide tracking number, transitioning order to SHIPPING.

**Acceptance Criteria:**
- [ ] PUT /orders/{id}/tracking accepts tracking_number and optional note
- [ ] Only allowed when order.status = PAID
- [ ] Sets status = SHIPPING, shipped_at = NOW()
- [ ] Returns 409 if not in PAID, 403 if not the seller
- [ ] Produces order.shipped Kafka event

**Related:** BR-ORDER-013
**UC:** UC-ORDER-004
**API:** api-put-orders-ship.yaml

---

## FR-ORDER-010: Buyer Confirm Delivery

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-010

**Description:** Buyer shall confirm receipt, transitioning order to DELIVERED.

**Acceptance Criteria:**
- [ ] POST /orders/{id}/confirm-received (no body required)
- [ ] Only allowed when order.status = SHIPPING
- [ ] Sets status = DELIVERED, delivered_at = NOW()
- [ ] Sets return_window_end = NOW() + 7 days
- [ ] Produces order.delivered Kafka event
- [ ] Triggers seller payout transfer via Payment Service

**Related:** BR-ORDER-014
**UC:** UC-ORDER-005

---

## FR-ORDER-011: Auto-Confirm Delivery (JOB-22)

**Priority:** P1 (High)
**Stable ID:** FR-ORDER-011

**Description:** System shall auto-confirm delivery 7 days after shipping if buyer does not confirm.

**Acceptance Criteria:**
- [ ] JOB-22 runs periodically, checks SHIPPING orders older than 7 days
- [ ] Transitions to DELIVERED with autoDelivered=true flag
- [ ] Does NOT apply to orders in RETURNED status (RTS'd)
- [ ] Produces order.delivered Kafka event

**Related:** BR-ORDER-015

---

## FR-ORDER-012: Seller Return-To-Sender (RTS)

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-012

**Description:** Seller shall confirm goods returned, triggering automatic full refund without admin approval.

**Acceptance Criteria:**
- [ ] POST /orders/{id}/return-to-sender (multipart: evidence_images[] + return_tracking_number)
- [ ] Only allowed when order.status = SHIPPING
- [ ] Transitions to RETURNED
- [ ] Produces `order.returned` Kafka event (consumed by Refund Service to create and auto-process a full refund)
- [ ] Restores stock via `variant.stock_updated` (emitted by Product Service)
- [ ] Evidence images stored via MinIO

**Related:** BR-ORDER-016, BR-ORDER-022
**UC:** UC-ORDER-006
**API:** api-post-orders-return.yaml

---

## FR-ORDER-013: Buyer Refund Request

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-013

**Description:** Buyer shall request partial or full refund within the return window.

**Acceptance Criteria:**
- [ ] POST /orders/{id}/refunds accepts reason, items[], evidence_images[]
- [ ] Only allowed when order.status = DELIVERED and within return_window_end
- [ ] Partial: specifies order_item_id + quantity per item
- [ ] Full: POST /orders/parent/{parentOrderId}/refund
- [ ] Order Service routes request details to Refund Service to create `REFUNDS` with status = PENDING (awaiting ADMIN approval)
- [ ] Produces `refund.requested` Kafka event

**Related:** BR-ORDER-017, BR-ORDER-018, BR-ORDER-019
**UC:** UC-ORDER-006

---

## FR-ORDER-014: Seller Order Listing

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-014

**Description:** Seller shall list orders for their shop with filtering and pagination.

**Acceptance Criteria:**
- [ ] GET /sellers/me/orders returns paginated list
- [ ] Filterable by status, from_date, to_date
- [ ] Only returns sub-orders belonging to authenticated seller
- [ ] Includes buyer info and order items summary

**Related:** UC-ORDER-007
**API:** api-get-orders.yaml

---

## FR-ORDER-015: Seller Dashboard

**Priority:** P1 (High)
**Stable ID:** FR-ORDER-015

**Description:** Seller shall view order and revenue summary dashboard.

**Acceptance Criteria:**
- [ ] GET /sellers/me/dashboard returns order counts by status
- [ ] Includes total revenue, pending payouts
- [ ] Only accessible by SELLER role

---

## FR-ORDER-016: Kafka Event Production

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-016

**Description:** System shall produce Kafka events for all state transitions.

**Acceptance Criteria:**
- [x] `order.created` - after order creation from checkout event
- [x] `payment.requested` - after parent checkout saga starts
- [ ] `order.paid` â€” on payment success consumed
- [ ] `order.shipped` â€” on tracking update
- [ ] `order.delivered` â€” on delivery confirmation
- [ ] `order.cancelled` â€” on buyer/seller cancel
- [ ] `order.returned` â€” on RTS
- [x] `order.auto_cancelled` â€” on JOB-13/Axon timeout

**Related:** BR-ORDER-009, BR-ORDER-010, BR-ORDER-011, BR-ORDER-013, BR-ORDER-014, BR-ORDER-016

---

## FR-ORDER-017: Kafka Event Consumption

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-017

**Description:** System shall consume Kafka events to update order state.

**Acceptance Criteria:**
- [ ] `payment.success` â†’ mark orders as PAID
- [ ] `payment.failed` â†’ keep PENDING, unlock stock on exhaustion
- [ ] `refund.stripe_auto` â†’ mark as refunded (chargeback)
- [ ] `refund.rts_completed` â†’ update RTS completion status
- [ ] `stripe.transfer.reversed` â†’ log for reconciliation

---

## FR-ORDER-018: Saga Orchestration (Axon)

**Priority:** P0 (Critical)
**Stable ID:** FR-ORDER-018

**Description:** System shall use Axon Framework Saga to orchestrate the payment flow.

**Acceptance Criteria:**
- [ ] ParentOrderPaymentSaga starts on checkout
- [ ] Saga listens for payment.success / payment.failed
- [ ] On payment.success: mark orders PAID, end saga
- [ ] On payment.failed: mark orders CANCELLED, end saga
- [ ] Timeout: 30 min regular / 10 min flash sale (Axon Deadline)

---

## Traceability Summary

| FR ID | BR IDs | UC IDs | API Contract |
|-------|--------|--------|-------------|
| FR-ORDER-001 | BR-001..005 | UC-ORDER-001 | product-service api-post-checkout-submit.yaml |
| FR-ORDER-002 | BR-ORDER-002 | UC-ORDER-001 | â€” |
| FR-ORDER-003 | BR-ORDER-003 | UC-ORDER-001 | â€” |
| FR-ORDER-004 | BR-ORDER-006 | â€” | â€” |
| FR-ORDER-005 | â€” | UC-ORDER-002 | api-get-orders.yaml |
| FR-ORDER-006 | â€” | UC-ORDER-002 | api-get-orders.yaml |
| FR-ORDER-007 | â€” | UC-ORDER-002 | â€” |
| FR-ORDER-008 | BR-ORDER-011 | UC-ORDER-003 | â€” |
| FR-ORDER-009 | BR-ORDER-013 | UC-ORDER-004 | api-put-orders-ship.yaml |
| FR-ORDER-010 | BR-ORDER-014 | UC-ORDER-005 | â€” |
| FR-ORDER-011 | BR-ORDER-015 | â€” | â€” |
| FR-ORDER-012 | BR-016,022 | UC-ORDER-006 | api-post-orders-return.yaml |
| FR-ORDER-013 | BR-017,018,019 | UC-ORDER-006 | â€” |
| FR-ORDER-014 | â€” | UC-ORDER-007 | api-get-orders.yaml |
| FR-ORDER-015 | â€” | â€” | â€” |
| FR-ORDER-016 | BR-009..016 | â€” | â€” |
| FR-ORDER-017 | â€” | â€” | â€” |
| FR-ORDER-018 | â€” | â€” | â€” |

---

## Cross-References

- **BR:** [br-checkout.md](../../business-rules/order-service/br-checkout.md)
- **BR:** [br-order-lifecycle.md](../../business-rules/order-service/br-order-lifecycle.md)
- **State:** [state-order.md](../../state-diagrams/order-service/state-order.md)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
