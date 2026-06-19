# ENTITY-ORDER-001: PARENT_ORDERS

**Stable ID:** ENTITY-ORDER-001
**Table:** `parent_orders`
**Schema:** PostgreSQL (order-service, port 8083)
**Last Updated:** 2026-05-10 (verified against Java source)

---

## ERD (Entity-Relationship Diagram)

```
┌──────────────────┐
│    CUSTOMERS      │
│──────────────────│
│ id (BIGSERIAL PK)│
└────────┬─────────┘
         │ FK
         ▼
┌──────────────────────────────────────────────────────────┐
│                    PARENT_ORDERS                          │
│──────────────────────────────────────────────────────────│
│ id            BIGSERIAL PK                                │
│ customer_id   BIGINT FK → customers.id NOT NULL           │
│ total_amt     DECIMAL(18,2) NOT NULL                      │
│ final_amt     DECIMAL(18,2) NOT NULL                      │
│ created_at    TIMESTAMP NOT NULL                          │
│ updated_at    TIMESTAMP NOT NULL                          │
└───────────────────────┬──────────────────────────────────┘
                        │ 1:N
                        ▼
┌──────────────────────────────────────────────────────────┐
│                       ORDERS                              │
│──────────────────────────────────────────────────────────│
│ id              BIGSERIAL PK                              │
│ parent_order_id BIGINT FK → parent_orders.id              │
│ ...                                                       │
└──────────────────────────────────────────────────────────┘
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK, NOT NULL | Auto-increment primary key |
| 2 | `customer_id` | BIGINT | FK → customers.id, NOT NULL | Buyer who placed the order |
| 3 | `total_amt` | DECIMAL(18,2) | NOT NULL | Sum of all sub-order total_amt |
| 4 | `final_amt` | DECIMAL(18,2) | NOT NULL | Actual amount charged to buyer via Stripe |
| 5 | `created_at` | TIMESTAMP | NOT NULL | Order creation timestamp |
| 6 | `updated_at` | TIMESTAMP | NOT NULL | Last modification timestamp |

Note: ParentOrder is a simple aggregate root. It does NOT contain `session_id`, `status`, or `payment_method` fields. Payment status is tracked at the sub-order (orders) level via `orders.status`. The checkout session (`stock_reservation.session_id`) is managed via the Product Service, not stored here.

---

## Indexes

| Index Name | Columns | Purpose |
|------------|---------|---------|
| `parent_orders_pkey` | `id` | Primary key lookup |

---

## Relationships

| From | To | Cardinality | On Delete |
|------|----|-------------|-----------|
| `parent_orders.customer_id` | `customers.id` | N:1 | RESTRICT |
| `orders.parent_order_id` | `parent_orders.id` | 1:N | RESTRICT |

---

## Business Rules

| Rule ID | Rule |
|---------|------|
| BR-ORDER-001 | One parent order per checkout (multi-vendor orders grouped) |
| BR-ORDER-002 | `parent_order.final_amt` = SUM(all sub-orders.final_amt) |
| BR-ORDER-003 | Parent order lifecycle tracked via sub-order status aggregation |

---

## Cross-References

- **ENTITY-ORDER-002:** [ORDERS](entity-order.md)
- **ENTITY-ORDER-003:** [ORDER_ITEMS](entity-order-item.md)
- **BR:** [br-checkout.md](../../business-rules/order-service/br-checkout.md)
- **BR:** [br-order-lifecycle.md](../../business-rules/order-service/br-order-lifecycle.md)
- **API:** [api-post-orders-checkout.yaml](../../api-contracts/order-service/api-post-orders-checkout.yaml)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
