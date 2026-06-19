# ENTITY-ORDER-003: ORDER_ITEMS

**Stable ID:** ENTITY-ORDER-003
**Table:** `order_items`
**Schema:** PostgreSQL (order-service, port 8083)
**Last Updated:** 2026-05-10 (verified against Java source)

---

## ERD (Entity-Relationship Diagram)

```
┌──────────────────────┐
│       ORDERS          │
│──────────────────────│
│ id (BIGSERIAL PK)    │
└──────────┬───────────┘
           │ FK
           ▼
┌──────────────────────────────────────────────────────────┐
│                     ORDER_ITEMS                           │
│──────────────────────────────────────────────────────────│
│ id                BIGSERIAL PK                            │
│ order_id          BIGINT FK → orders.id NOT NULL          │
│ sku_code          VARCHAR(100) NOT NULL                   │
│ variant_id        VARCHAR(100)                            │
│ name_snapshot     VARCHAR(500)                            │
│ image_snapshot    VARCHAR(1000)                           │
│ price_snapshot    DECIMAL(18,2)                           │
│ quantity          INT NOT NULL                            │
│ refunded_quantity INT DEFAULT 0                           │
│ fs_item_id        BIGINT NULLABLE                         │
│ created_at        TIMESTAMP NOT NULL                      │
└──────────────────────────────────────────────────────────┘
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK, NOT NULL | Auto-increment primary key |
| 2 | `order_id` | BIGINT | FK → orders.id, NOT NULL | Sub-order this line item belongs to |
| 3 | `sku_code` | VARCHAR(100) | NOT NULL | SKU code snapshot at time of purchase |
| 4 | `variant_id` | VARCHAR(100) | NULLABLE | Product variant ID (String, not UUID type) |
| 5 | `name_snapshot` | VARCHAR(500) | NULLABLE | Product name snapshot at time of purchase |
| 6 | `image_snapshot` | VARCHAR(1000) | NULLABLE | Product image URL snapshot |
| 7 | `price_snapshot` | DECIMAL(18,2) | NULLABLE | Unit price snapshot at time of purchase |
| 8 | `quantity` | INT | NOT NULL | Quantity purchased |
| 9 | `refunded_quantity` | INT | DEFAULT 0 | Quantity already refunded for this item |
| 10 | `fs_item_id` | BIGINT | NULLABLE | Flash sale item reference; NULL = regular purchase |
| 11 | `created_at` | TIMESTAMP | NOT NULL | Line item creation timestamp |

### Snapshot Fields

All `snapshot` fields capture values at purchase time and are **never updated** after creation.

| Snapshot Field | Source at Checkout | Purpose |
|----------------|-------------------|---------|
| `sku_code` | `product_variant.variant_code` | Identify SKU sold |
| `name_snapshot` | `product.name` | Display in order history |
| `image_snapshot` | First product image URL | Display in order detail |
| `price_snapshot` | `product_variant.price` (or flash sale price) | Financial record |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `order_items_pkey` | `id` | PRIMARY KEY B-tree | Primary key lookup |
| `idx_order_items_order` | `order_id` | B-tree | Find all items in an order |

---

## Relationships

| From | To | Cardinality | On Delete |
|------|----|-------------|-----------|
| `order_items.order_id` | `orders.id` | N:1 (via @ManyToOne) | CASCADE |

---

## Business Rules

| Rule ID | Rule |
|---------|------|
| BR-ORDER-020 | All snapshot fields populated at checkout and immutable thereafter |
| BR-ORDER-021 | `quantity` must be >= 1 |
| BR-ORDER-022 | `fs_item_id` populated only when item was purchased via flash sale |
| BR-ORDER-023 | Refund quantity per item cannot exceed `quantity - refunded_quantity` |

---

## Cross-References

- **ENTITY-ORDER-001:** [PARENT_ORDERS](entity-parent-order.md)
- **ENTITY-ORDER-002:** [ORDERS](entity-order.md)
- **BR:** [br-checkout.md](../../business-rules/order-service/br-checkout.md)
- **BR:** [br-order-lifecycle.md](../../business-rules/order-service/br-order-lifecycle.md)
- **API:** [api-post-orders-checkout.yaml](../../api-contracts/order-service/api-post-orders-checkout.yaml)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
