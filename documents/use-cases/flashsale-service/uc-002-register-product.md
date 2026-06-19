# UC-FLASHSALE-002: Seller Register Product in Flash Sale

**Stable ID:** `UC-FLASHSALE-002`
**Actor:** Seller
**Priority:** HIGH
**Auth:** JWT (SELLER)

---

## Brief
A seller registers a product in an upcoming flash sale session before the registration deadline. Registration is auto-approved -- the FS_ITEMS record is created immediately with the session's discount percentage.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Actor is authenticated as SELLER |
| P2 | Session exists with `status = UPCOMING` |
| P3 | `NOW() < session.registration_deadline` (BR-FLASHSALE-002) |
| P4 | Product belongs to authenticated seller |
| P5 | Product not already registered in this session (BR-FLASHSALE-010) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Seller | Submits `POST /flash-sales/{id}/items` with `product_id` and optional `discount_applied` |
| 2 | System | Validates session exists and `status = UPCOMING` |
| 3 | System | Validates `NOW() < session.registration_deadline` (BR-FLASHSALE-002) |
| 4 | System | Validates product belongs to seller (calls Product Service) |
| 5 | System | Validates product not already registered: checks UNIQUE(session_id, product_id) (BR-FLASHSALE-010) |
| 6 | System | Sets `discount_applied = session.discount_percentage` (if not overridden by seller) |
| 7 | System | Inserts row into `fs_items` with `registered_at = NOW()` |
| 8 | System | Publishes Kafka event `flash_sale.item_registered` (FR-FLASHSALE-012) |
| 9 | System | Returns `201 Created` with FS_ITEMS record |

---

## Alternate Flows

| # | Trigger | Action |
|---|---------|--------|
| A1 | Session not found | Return `404 SESSION_NOT_FOUND` |
| A2 | Session status is ACTIVE or ENDED | Return `400 SESSION_NOT_UPCOMING` |
| A3 | `NOW() >= registration_deadline` | Return `400 REGISTRATION_CLOSED` |
| A4 | Product does not belong to seller | Return `403 PRODUCT_NOT_OWNED` |
| A5 | Product already registered in session | Return `409 PRODUCT_ALREADY_REGISTERED` |

---

## Postconditions

| # | Condition |
|---|-----------|
| PC1 | `fs_items` row exists with `session_id`, `product_id`, `discount_applied`, `seller_id`, `registered_at` |
| PC2 | Product is locked to this session (can't register again — UNIQUE constraint) |
| PC3 | Kafka event `flash_sale.item_registered` published |

---

## Cross-References

| Reference | Description |
|-----------|-------------|
| FR-FLASHSALE-004 | Seller registers product in session |
| FR-FLASHSALE-005 | System auto-approves registration |
| BR-FLASHSALE-002 | Registration deadline window |
| BR-FLASHSALE-010 | Unique product per session |
| ENTITY-FLASHSALE-002 | FS_ITEMS table |

---

## Related Use Cases

| UC | Relationship |
|----|-------------|
| UC-FLASHSALE-001 | Admin creates the session |
| UC-FLASHSALE-003 | View registered items in session |
| UC-FLASHSALE-005 | Customer buys the registered item |

---

*Generated: 2026-05-09*
