# FR-PRODUCT-016 through FR-PRODUCT-022: Cart Functional Requirements

> **Service**: product-service (Port 8084)
> **Domain**: Cart
> **Source**: 02_API_product_service.md Cart Endpoints, product_service_ui_logic.md Sections 3-4

---

## FR-PRODUCT-016: Get Customer Cart

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Customer (JWT required) |
| **Endpoint** | `GET /cart` |
| **Description** | Return the customer's cart grouped by seller. Enrich each item with real-time variant data (price, stock, status) via lazy evaluation. Return flags for price_changed, out_of_stock, unavailable. |
| **Acceptance Criteria** | AC1: Cart grouped by seller_id. AC2: Items enriched with current variant prices. AC3: price_changed flag when snapshot != current price. AC4: out_of_stock/unavailable flags when applicable. AC5: Expired flash sale items excluded (removed by JOB-07). |
| **Related** | ENTITY-PRODUCT-006, ENTITY-PRODUCT-007, UC-PRODUCT-008, BR-PRODUCT-011 |

---

## FR-PRODUCT-017: Add Item to Cart

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Customer (JWT required) |
| **Endpoint** | `POST /cart/items` |
| **Description** | Add a variant (SKU) to the cart. If already present, increment quantity. Snapshot price, variant name, and image at add time. Validate stock availability.|
| **Acceptance Criteria** | AC1: UPSERT behavior (increment if exists, create if not). AC2: quantity > 0, <= 1000. AC3: Returns 422 if insufficient stock. AC4: Returns 422 if variant inactive. AC5: Emits Kafka event. |
| **Related** | ENTITY-PRODUCT-007, UC-PRODUCT-009, BR-PRODUCT-010, BR-PRODUCT-011, BR-PRODUCT-012 |

---

## FR-PRODUCT-018: Update Cart Item Quantity

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Customer (JWT required) |
| **Endpoint** | `PUT /cart/items/{itemId}` |
| **Description** | Update the quantity of an existing cart item. Validates against current stock_available. Setting quantity to 0 is invalid (use DELETE). |
| **Acceptance Criteria** | AC1: quantity > 0, <= stock_available. AC2: Returns 422 if exceeds stock. AC3: Returns 404 if item doesn't exist or doesn't belong to user. |
| **Related** | ENTITY-PRODUCT-007, UC-PRODUCT-010, BR-PRODUCT-012 |

---

## FR-PRODUCT-019: Remove Item from Cart

| Attribute | Value |
|-----------|-------|
| **Priority** | HIGH |
| **Actor** | Customer (JWT required) |
| **Endpoint** | `DELETE /cart/items/{itemId}` |
| **Description** | Remove a single item from the cart. |
| **Acceptance Criteria** | AC1: Item removed. AC2: Returns 404 if item not found or not owned by user. |
| **Related** | ENTITY-PRODUCT-007, UC-PRODUCT-011 |

---

## FR-PRODUCT-020: Clear Entire Cart

| Attribute | Value |
|-----------|-------|
| **Priority** | MEDIUM |
| **Actor** | Customer (JWT required) |
| **Endpoint** | `DELETE /cart` |
| **Description** | Remove all items from the customer's cart. Cart record itself persists (status remains active). |
| **Acceptance Criteria** | AC1: All cart_items deleted. AC2: Cart record retained. |
| **Related** | ENTITY-PRODUCT-006, UC-PRODUCT-008 |

---

## FR-PRODUCT-021: Checkout Preview

| Attribute | Value |
|-----------|-------|
| **Priority** | CRITICAL |
| **Actor** | Customer (JWT required) |
| **Endpoint** | `POST /v1/cart/checkout/preview` |
| **Description** | Validates ALL cart items: price must match snapshot, stock must be sufficient, variant must be active. If all pass, returns a `preview_token` (TTL 10 min) stored in Redis. If any check fails, returns 409 Conflict with per-item error details -- UI must force cart refresh before retrying. |
| **Acceptance Criteria** | AC1: Price mismatch -> 409 with `PRICE_CHANGED` detail. AC2: Insufficient stock -> 409 with `INSUFFICIENT_STOCK` detail. AC3: Inactive/unavailable variant -> 409 with `VARIANT_INACTIVE` detail. AC4: All checks pass -> returns `preview_token` + `expires_at` (10 min TTL). AC5: Token is invalidated after successful checkout. |
| **Related** | ENTITY-PRODUCT-007, BR-PRODUCT-011, BR-PRODUCT-012 |

---

## FR-PRODUCT-022: Checkout Submit

| Attribute | Value |
|-----------|-------|
| **Priority** | CRITICAL |
| **Actor** | Customer (JWT required) |
| **Endpoint** | `POST /v1/cart/checkout/submit` |
| **Description** | Receives a valid `preview_token` and address. Re-validates all items one final time. On success: reserves stock (PENDING), stores checkout session in Redis (TTL 15 min), emits `order.checkout_submitted` Kafka event to Order Service, and invalidates the `preview_token`. On failure: returns 409 Conflict with details. |
| **Acceptance Criteria** | AC1: Missing/invalid `preview_token` -> 409. AC2: Stock or price changed since preview -> 409 Conflict with details. AC3: All checks pass -> stock reserved (PENDING), `order.checkout_submitted` event sent, `preview_token` invalidated, returns `session_id`. AC4: On `order.paid` event -> confirmReservation(). AC5: On `order.payment_failed` event -> releaseReservation(). |
| **Related** | ENTITY-PRODUCT-007, ENTITY-PRODUCT-008, KafkaTopics.ORDER_CHECKOUT_SUBMITTED, KafkaTopics.ORDER_PAID, KafkaTopics.ORDER_PAYMENT_FAILED |

---

## FR-PRODUCT-023: Cart Cleanup on Events

| Attribute | Value |
|-----------|-------|
| **Priority** | MEDIUM |
| **Actor** | System (Kafka consumer) |
| **Description** | Consume events to maintain cart integrity: (a) `order.cancelled` -- release stock reservations; (b) `flash_sale.session_ended` -- JOB-07 removes expired flash sale items; (c) `order.returned` -- restore stock. |
| **Acceptance Criteria** | AC1: Stock released on `order.cancelled`. AC2: Expired flash items removed on `flash_sale.session_ended`. AC3: Stock restored on `order.returned`. |
| **Related** | ENTITY-PRODUCT-006, ENTITY-PRODUCT-007, KAFKA_EVENTS.md |
