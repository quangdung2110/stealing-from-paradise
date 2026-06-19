# ENTITY-ORDER-002: ORDERS

**Stable ID:** ENTITY-ORDER-002
**Table:** `orders`
**Schema:** PostgreSQL (order-service, port 8083)
**Last Updated:** 2026-05-10 (verified against Java source)

---

## ERD (Entity-Relationship Diagram)

```
┌──────────────────┐    ┌──────────────────┐
│  PARENT_ORDERS    │    │     SELLERS       │
│──────────────────│    │──────────────────│
│ id (BIGSERIAL PK)│    │ id (BIGSERIAL PK)│
└────────┬─────────┘    └────────┬─────────┘
         │ FK                    │ FK
         │                       │
┌──────────────────┐             │
│    CUSTOMERS      │             │
│──────────────────│             │
│ id (BIGSERIAL PK)│             │
└────────┬─────────┘             │
         │ FK                    │
         ▼                       ▼
┌──────────────────────────────────────────────────────────┐
│                        ORDERS                             │
│──────────────────────────────────────────────────────────│
│ id                BIGSERIAL PK                            │
│ parent_order_id   BIGINT FK → parent_orders.id NOT NULL   │
│ seller_id         BIGINT FK → sellers.id NOT NULL         │
│ order_code        VARCHAR UNIQUE NOT NULL                 │
│ customer_id       BIGINT FK → customers.id NOT NULL       │
│ total_amt         DECIMAL(18,2) NOT NULL                  │
│ final_amt         DECIMAL(18,2) NOT NULL                  │
│ status            VARCHAR(50) NOT NULL DEFAULT 'PENDING'   │
│ cancelled_by      VARCHAR(50)                             │
│ cancel_reason     TEXT                                    │
│ is_flash_sale     BOOLEAN DEFAULT FALSE                   │
│ shipping_address  JSONB                                   │
│ shipping_deadline TIMESTAMP                               │
│ tracking_number   VARCHAR                                 │
│ version           INTEGER DEFAULT 0                       │
│ created_at        TIMESTAMP NOT NULL                      │
│ updated_at        TIMESTAMP NOT NULL                      │
└───────────────────────┬──────────────────────────────────┘
                        │ 1:N
                        ▼
┌──────────────────────────────────────────────────────────┐
│                     ORDER_ITEMS                           │
│──────────────────────────────────────────────────────────│
│ id         BIGSERIAL PK                                   │
│ order_id   BIGINT FK → orders.id                          │
│ ...                                                       │
└──────────────────────────────────────────────────────────┘
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK, NOT NULL | Auto-increment primary key |
| 2 | `parent_order_id` | BIGINT | FK → parent_orders.id, NOT NULL | Parent order grouping sub-orders |
| 3 | `seller_id` | BIGINT | FK → sellers.id, NOT NULL | Seller fulfilling this sub-order |
| 4 | `order_code` | VARCHAR | UNIQUE, NOT NULL | Human-readable display code |
| 5 | `customer_id` | BIGINT | FK → customers.id, NOT NULL | Buyer |
| 6 | `total_amt` | DECIMAL(18,2) | NOT NULL | Sum of item prices before discounts |
| 7 | `final_amt` | DECIMAL(18,2) | NOT NULL | Actual amount charged |
| 8 | `status` | VARCHAR(50) | NOT NULL, DEFAULT 'PENDING' | Sub-order lifecycle status |
| 9 | `cancelled_by` | VARCHAR(50) | NULL | Actor who cancelled: BUYER/SELLER/SYSTEM |
| 10 | `cancel_reason` | TEXT | NULL | Reason provided at cancellation |
| 11 | `is_flash_sale` | BOOLEAN | DEFAULT FALSE | Whether this order is from flash sale |
| 12 | `shipping_address` | JSONB | NULL | Snapshot of delivery address at checkout |
| 13 | `shipping_deadline` | TIMESTAMP | NULL | Deadline for seller to provide tracking |
| 14 | `tracking_number` | VARCHAR | NULL | Shipping carrier tracking number |
| 15 | `version` | INTEGER | NOT NULL, DEFAULT 0 | Optimistic locking (@Version) |
| 16 | `created_at` | TIMESTAMP | NOT NULL | Order creation timestamp |
| 17 | `updated_at` | TIMESTAMP | NOT NULL | Last modification timestamp |

Note: The following fields from older docs do NOT exist in the Java entity: `net_payout_amount`, `carrier`, `paid_at`, `return_window_end`, `shipped_at`, `delivered_at`. These are derived/computed or stored elsewhere.

### Status Enum (5 Core States)

| # | Status | Description |
|---|--------|-------------|
| 1 | `PENDING` | Order created, awaiting payment |
| 2 | `PAID` | Payment confirmed via Stripe webhook |
| 3 | `SHIPPING` | Seller uploaded tracking number |
| 4 | `DELIVERED` | Buyer confirmed receipt |
| 5 | `RETURNED` | Seller confirmed RTS (Return To Sender) |
| 6 | `REFUNDED` | Full refund processed (via Kafka `refund.admin_approved`) |
| 7 | `PARTIALLY_REFUNDED` | Partial refund processed |
| 8 | `CANCELLED` | Order cancelled before shipping |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `orders_pkey` | `id` | PRIMARY KEY B-tree | Primary key lookup |
| `idx_orders_customer` | `customer_id` | B-tree | Buyer order listing |
| `idx_orders_seller` | `seller_id` | B-tree | Seller order listing |
| `idx_orders_parent_order` | `parent_order_id` | B-tree | Find sub-orders by parent |
| `idx_orders_status` | `status` | B-tree | Filter by status |

---

## Relationships

| From | To | Cardinality | On Delete |
|------|----|-------------|-----------|
| `orders.parent_order_id` | `parent_orders.id` | N:1 | RESTRICT |
| `orders.seller_id` | `sellers.id` | N:1 | RESTRICT |
| `orders.customer_id` | `customers.id` | N:1 | RESTRICT |
| `order_items.order_id` | `orders.id` | 1:N | CASCADE |

---

## JSONB Structure: shipping_address

```json
{
  "full_address": "123 Nguyen Trai, Phuong 2, Q.3, TP.HCM",
  "province_id": 79,
  "district_id": 760,
  "address_id": 7
}
```

---

## Business Rules

| Rule ID | Rule |
|---------|------|
| BR-ORDER-010 | Sub-order created per seller during multi-vendor checkout |
| BR-ORDER-011 | `order_code` format: `OR-YYYYMMDD-{id}` |
| BR-ORDER-012 | `shipping_deadline` = `created_at` + 3 days |
| BR-ORDER-014 | Cancel only allowed when status = PENDING |
| BR-ORDER-015 | Ship (tracking) only allowed when status = PAID |
| BR-ORDER-016 | Confirm delivery only allowed when status = SHIPPING |
| BR-ORDER-017 | RTS only allowed when status = SHIPPING |
| BR-ORDER-018 | Buyer refund request only allowed when status = DELIVERED AND within 7 days |

---

## Cross-References

- **ENTITY-ORDER-001:** [PARENT_ORDERS](entity-parent-order.md)
- **ENTITY-ORDER-003:** [ORDER_ITEMS](entity-order-item.md)
- **BR:** [br-checkout.md](../../business-rules/order-service/br-checkout.md)
- **BR:** [br-order-lifecycle.md](../../business-rules/order-service/br-order-lifecycle.md)
- **State:** [state-order.md](../../state-diagrams/order-service/state-order.md)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
