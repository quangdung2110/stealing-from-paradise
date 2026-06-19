# UC-PRODUCT-007: Reserve Stock During Checkout

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-007 |
| **Actor** | Product Service checkout submit |
| **Priority** | CRITICAL |
| **Precondition** | Buyer has a valid `preview_token` |
| **Postcondition** | Stock reserved with 15-minute TTL and checkout event emitted |

---

## Main Flow

### Phase 1: Checkout Preview

1. Buyer calls `POST /v1/cart/checkout/preview` with `item_ids[]`.
2. Product Service validates cart ownership, active variants, current price, and available stock.
3. Product Service stores the preview in Redis and returns a one-time `preview_token` with 10-minute TTL.

### Phase 2: Checkout Submit and Reservation

1. Buyer calls `POST /v1/cart/checkout/submit` with `preview_token` and `address_id`.
2. Product Service validates the address via `order.address.request` / `order.address.response`.
3. Product Service revalidates stock and price.
4. Product Service reserves each variant with a shared `session_id`.
5. Product Service stores a checkout session in Redis for 15 minutes.
6. Product Service emits `order.checkout_submitted` with item snapshots, address snapshot, totals, and `session_id`.
7. Order Service consumes the event and creates parent/sub-orders.

### Phase 3: Confirm or Release

```
order.paid:
  stock_reservation -> CONFIRMED
  cart_items for the reserved variants are deleted

order.payment_failed / order.cancelled / order.auto_cancelled:
  stock_reservation -> RELEASED
  product_variant.stock_quantity is restored

Reservation cleanup:
  expired PENDING reservations are released by product-service scheduler
```

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Invalid preview token | 400 / 409 depending on validation path |
| Address missing or not owned by buyer | 409 |
| Stock or price changed after preview | 409 with item-level details |
| Concurrent reservation | Pessimistic lock serializes reservations |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-014 | Reserve stock during checkout |
| FR-PRODUCT-015 | Release expired reservations |
| BR-PRODUCT-005 | Pessimistic locking for concurrent reservations |
| BR-PRODUCT-007 | Reservation expiry (15 min TTL) |
| ENTITY-PRODUCT-005 | STOCK_RESERVATION |
| state-stock-reservation.md | pending -> confirmed / released |
