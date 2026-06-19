# BR-ORDER-010 to BR-ORDER-026: Order Lifecycle Business Rules

**Stable IDs:** BR-ORDER-010 through BR-ORDER-026
**Domain:** Order Lifecycle (8-state transitions)
**Last Updated:** 2026-05-10 (BR-ORDER-021 reactivated SELLER cancel; added BR-ORDER-026)

---

## State Machine Overview (8 States)

```
                           ┌──────────────────────────────────────┐
                           │                                      │
                           ▼                                      │
PENDING ──▶ PAID ──▶ SHIPPING ──▶ DELIVERED ──▶ REFUNDED        │
   │          │         │            │              ▲              │
   │          │         │            │              │              │
   │          │         ▼            ├──────────────┘              │
   │          │      RETURNED        │                             │
   │          │         │            │                             │
   │          │         │            ▼                             │
   │          │         │      PARTIALLY_REFUNDED                  │
   │          │         │                                          │
   ▼          ▼         ▼                                          │
CANCELLED (terminal for PENDING/PAID)                              │
```

---

## BR-ORDER-010: PENDING → PAID

**Rule:** Transition from PENDING to PAID occurs when Stripe payment succeeds.

| Actor | System (Kafka consumer) |
|-------|------------------------|
| Trigger | `payment.success` Kafka event consumed |
| Precondition | `orders.status = PENDING` |
| Postcondition | `orders.status = PAID`, `orders.paid_at = NOW()` |
| Side Effect | `parent_orders.status = PAID` (if all sub-orders paid) |

**Kafka Event Produced:** `order.paid`

**Error:**
| Status | Condition |
|--------|-----------|
| 409 | Order not in PENDING status |

---

## BR-ORDER-011: PENDING/PAID → CANCELLED

**Rule:** Order can be cancelled from PENDING or PAID status (PAID cancellation only before seller ships).

| Actor | BUYER hoặc SELLER |
|-------|-------------------|
| Trigger | POST /orders/{id}/cancel |
| Precondition (BUYER) | `orders.status IN (PENDING, PAID)` AND user is the order customer |
| Precondition (SELLER) | `orders.status = PAID` AND user is the order seller (BR-ORDER-021) |
| Postcondition | `orders.status = CANCELLED`, `cancelled_by = BUYER\|SELLER`, `cancel_reason = {reason}` |
| Side Effect | Stock released via `variant.stock_updated` (emitted by Product Service on stock restore), `order.cancelled` Kafka event; nếu SELLER hủy thêm `seller.order_cancelled` (BR-ORDER-026) |

**Kafka Event Produced:** `order.cancelled` (luôn); `seller.order_cancelled` (khi `cancelled_by = SELLER`).

**Error:**
| Status | Condition |
|--------|-----------|
| 409 | Order not in PENDING or PAID status |
| 409 | Order is PAID and seller has already shipped (SHIPPING or beyond) |
| 409 | SELLER cố hủy đơn ở trạng thái PENDING (chỉ BUYER mới hủy được PENDING) |
| 403 | User not the order owner (không phải buyer cũng không phải seller) |

---

## BR-ORDER-012: Auto-Cancel on Payment Timeout (JOB-13)

**Rule:** If payment is not confirmed within timeout, order is auto-cancelled.

| Actor | SYSTEM (JOB-13) |
|-------|-----------------|
| Trigger | Payment timeout (30 min regular / 10 min flash sale) |
| Precondition | `orders.status = PENDING` AND `NOW() > created_at + timeout` |
| Postcondition | `orders.status = CANCELLED`, `cancelled_by = SYSTEM` |
| Side Effect | `order.auto_cancelled` Kafka event, stock unlocked |

**Kafka Event Produced:** `order.auto_cancelled`

---

## BR-ORDER-013: PAID → SHIPPING

**Rule:** Seller provides tracking number to transition to SHIPPING.

| Actor | SELLER |
|-------|--------|
| Trigger | PUT /orders/{id}/tracking |
| Precondition | `orders.status = PAID` AND seller is order owner |
| Postcondition | `orders.status = SHIPPING`, `tracking_number = {value}`, `shipped_at = NOW()` |
| Side Effect | `order.shipped` Kafka event, notification to buyer |

**Kafka Event Produced:** `order.shipped`

**Error:**
| Status | Condition |
|--------|-----------|
| 409 | Order not in PAID status |
| 403 | Seller not the sub-order owner |

---

## BR-ORDER-014: SHIPPING → DELIVERED (Buyer Confirm)

**Rule:** Buyer confirms receipt to complete delivery.

| Actor | BUYER |
|-------|-------|
| Trigger | POST /orders/{id}/confirm-received |
| Precondition | `orders.status = SHIPPING` |
| Postcondition | `orders.status = DELIVERED`, `delivered_at = NOW()`, `return_window_end = NOW() + 7 days` |
| Side Effect | `order.delivered` Kafka event, triggers seller payout transfer |

**Kafka Event Produced:** `order.delivered`

**Error:**
| Status | Condition |
|--------|-----------|
| 409 | Order not in SHIPPING status |

---

## BR-ORDER-015: SHIPPING → DELIVERED (Auto-Confirm JOB-22)

**Rule:** If buyer does not confirm within 7 days after shipping, auto-confirm delivery.

| Actor | SYSTEM (JOB-22) |
|-------|-----------------|
| Trigger | `orders.status = SHIPPING` AND `NOW() > shipped_at + 7 days` |
| Precondition | Order has not been RTS'd |
| Postcondition | `orders.status = DELIVERED`, `delivered_at = NOW()` |
| Side Effect | `order.delivered` Kafka event (autoDelivered=true) |

**Important:** JOB-22 does NOT apply if the order was RTS'd (RETURNED status).

---

## BR-ORDER-016: SHIPPING → RETURNED (RTS)

**Rule:** Seller confirms goods returned to sender, triggering auto-refund.

| Actor | SELLER |
|-------|--------|
| Trigger | POST /orders/{id}/return-to-sender |
| Precondition | `orders.status = SHIPPING` |
| Postcondition | `orders.status = RETURNED` |
| Side Effect | Full refund auto-created, stock restored, `order.returned` Kafka event |

**Kafka Event Produced:** `order.returned`

**Required Inputs:**
| Field | Type | Required |
|-------|------|----------|
| `return_tracking_number` | VARCHAR | Yes |
| `evidence_images` | File[] | Yes (multipart) |
| `note` | VARCHAR | No |

**Error:**
| Status | Condition |
|--------|-----------|
| 409 | Order already RETURNED or has pending refund |
| 422 | Order not in SHIPPING status |
| 403 | Not the order's seller |
| 400 | Missing evidence_images |

---

## BR-ORDER-017: Refund Request Window

**Rule:** Buyer may request refund within 7 days of delivery.

| Condition | Outcome |
|-----------|---------|
| IF `orders.status = DELIVERED` AND `NOW() <= return_window_end` | THEN refund request allowed |
| IF `orders.status != DELIVERED` | THEN reject with 422 |
| IF `NOW() > return_window_end` | THEN reject with 422 "Return window expired" |

**Formula:** `return_window_end = delivered_at + 7 days`

---

## BR-ORDER-018: DELIVERED → REFUNDED (Full Refund)

**Rule:** Full refund transitions sub-order to REFUNDED.

| Actor | BUYER (request) → ADMIN (approve) |
|-------|----------------------------------|
| Trigger | Admin approves full refund after buyer request |
| Precondition | `orders.status IN (DELIVERED, SHIPPING, PAID)` AND full refund approved |
| Postcondition | `orders.status = REFUNDED` |
| Side Effect | `refund.admin_approved` Kafka event |

---

## BR-ORDER-019: DELIVERED → PARTIALLY_REFUNDED

**Rule:** Partial refund transitions sub-order to PARTIALLY_REFUNDED.

| Actor | BUYER (request) → ADMIN (approve) |
|-------|----------------------------------|
| Trigger | Admin approves partial refund |
| Precondition | `orders.status IN (DELIVERED, SHIPPING, PAID)` AND partial refund processed |
| Postcondition | `orders.status = PARTIALLY_REFUNDED` |
| Side Effect | `refund.admin_approved` Kafka event |

---

## BR-ORDER-020: RETURNED → REFUNDED (RTS Auto-Refund)

**Rule:** RTS auto-refund completes, transitioning to REFUNDED.

| Actor | SYSTEM (auto-refund via Payment Service) |
|-------|------------------------------------------|
| Trigger | `refund.rts_completed` Kafka event consumed |
| Precondition | `orders.status = RETURNED` |
| Postcondition | `orders.status = REFUNDED` |

---

## BR-ORDER-021: Cancellation Actor Rules

**Rule:** Who can cancel depends on order status and role.

| Cancelled By | Allowed | Condition |
|-------------|---------|-----------|
| BUYER | Yes | `orders.status IN (PENDING, PAID)` AND user is customer AND seller has not shipped |
| SELLER | Yes | `orders.status = PAID` AND user is the order's seller (BR-ORDER-026) |
| SYSTEM | Yes | Payment timeout (JOB-13/JOB-22) hoặc shipping deadline exceeded |

**Forbidden cases:**
- SELLER không được hủy đơn ở `PENDING` (đơn chưa thanh toán — buyer tự hủy hoặc system auto-cancel).
- SELLER không được hủy đơn ở `SHIPPING` hoặc sau đó — phải dùng RTS (BR-ORDER-016).

**Note:** Cập nhật 2026-05-10: SELLER cancellation đã được **đưa lại MVP** (override quyết định trước đây). Use case xem UC-ORDER-008.

---

## BR-ORDER-022: RTS vs Buyer Refund Distinction

| Aspect | RTS (BR-ORDER-016) | Buyer Refund (BR-ORDER-017/018) |
|--------|-------------------|--------------------------------|
| Initiator | SELLER | BUYER |
| Status Required | SHIPPING | DELIVERED |
| Admin Approval | NOT required (auto) | REQUIRED |
| Return Tracking | Mandatory | Optional |
| Stock Restore | Automatic | Manual |
| Kafka Event | `order.returned` | `refund.requested` |

---

## BR-ORDER-023: Parent Order Status Sync

**Rule:** Parent order status reflects aggregate sub-order states.

| Parent Status | Sub-Order States |
|---------------|-----------------|
| PENDING_PAYMENT | Any sub-order is PENDING |
| PAID | All sub-orders are PAID or beyond |
| CANCELLED | All sub-orders are CANCELLED |

---

## BR-ORDER-024: Immutable Shipping Snapshot

**Rule:** `shipping_address` (JSONB) is captured at checkout and never updated.

| Condition | Outcome |
|-----------|---------|
| IF checkout succeeds | THEN `shipping_address` written once |
| IF order updated later | THEN `shipping_address` is NOT modified |

---

## BR-ORDER-025: Stock Reservation Release

**Rule:** Stock reservations are released when order is cancelled or stock check fails.

| Trigger | Action |
|---------|--------|
| Checkout fails (stock insufficient) | Release all reservations immediately |
| Order cancelled (BUYER/SELLER) | Release reserved stock via `variant.stock_updated` (Product Service) |
| Order auto-cancelled (JOB-13) | Release reserved stock via `variant.stock_updated` (Product Service) |
| Reservation TTL expires (>15 min) | Product Service auto-releases |

---

## BR-ORDER-026: Seller Cancel Order (PAID, before SHIPPING)

**Rule:** Khi seller không thể fulfill đơn đã thanh toán, có thể hủy với lý do; system tự refund toàn bộ và release tồn kho.

| Actor | SELLER |
|-------|--------|
| Trigger | POST /orders/{id}/cancel với `reason` (bắt buộc) |
| Precondition | `orders.status = PAID` AND `seller_id = current_user` AND `tracking_number IS NULL` (chưa ship) |
| Postcondition | `orders.status = CANCELLED`, `cancelled_by = SELLER`, `cancel_reason = {reason}` |
| Side Effect 1 | Emit `order.cancelled` (Product Service release stock; Identity audit) |
| Side Effect 2 | Emit `seller.order_cancelled` (Payment Service trigger full refund; Notification gửi buyer) |
| Side Effect 3 | Tăng counter `seller_cancel_count` của seller (để Admin theo dõi seller bị hủy nhiều) — *tracking outside DB scope, MVP không bắt buộc* |

**Refund chain:**
- `seller.order_cancelled` → Payment Service tạo full refund tự động (không cần admin duyệt) — `refund.type = FULL`, `reason = SELLER_CANCEL`.
- Khi Stripe refund hoàn tất, Payment Service emit `refund.rts_completed`-tương đương để Order Service cập nhật `orders.status = REFUNDED` (giữ trạng thái CANCELLED nếu đã ở đó — tùy implementation, *clarify in code*).

**Required Inputs:**
| Field | Type | Required | Note |
|-------|------|----------|------|
| `reason` | string | Yes | min 10 chars (e.g. "Hết hàng do seller", "Sai mô tả") |
| `note` | string | No | ≤1000 chars |

**Error:**
| Status | Condition |
|--------|-----------|
| 409 | Order not in PAID status |
| 409 | `tracking_number` đã set (đã ship — phải dùng RTS) |
| 403 | Not the order's seller |
| 422 | `reason` thiếu hoặc quá ngắn |

**Notification:** NOTIF-ORDER-CANCELLED-BY-SELLER (xem br-notification.md).

---

## Cross-References

- **ENTITY-ORDER-001:** [PARENT_ORDERS](../../data-models/order-service/entity-parent-order.md)
- **ENTITY-ORDER-002:** [ORDERS](../../data-models/order-service/entity-order.md)
- **BR:** [br-checkout.md](br-checkout.md)
- **UC-003:** [uc-003-cancel-order.md](../../use-cases/order-service/uc-003-cancel-order.md)
- **UC-008:** [uc-008-seller-cancel-order.md](../../use-cases/order-service/uc-008-seller-cancel-order.md)
- **UC-004:** [uc-004-ship-order.md](../../use-cases/order-service/uc-004-ship-order.md)
- **UC-005:** [uc-005-confirm-delivery.md](../../use-cases/order-service/uc-005-confirm-delivery.md)
- **UC-006:** [uc-006-request-return.md](../../use-cases/order-service/uc-006-request-return.md)
- **FR:** [fr-order.md](../../srs/fr/order-service/fr-order.md)
- **State:** [state-order.md](../../state-diagrams/order-service/state-order.md)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
