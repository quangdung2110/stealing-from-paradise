# ENTITY-PAYMENT-002: Transaction

**Domain**: Payment Service  
**Table**: `TRANSACTIONS`  
**Purpose**: Records each payment transaction flowing through Stripe, linking the parent order to Stripe PaymentIntents and storing the raw gateway response.  
**References**: [database-entities.md](../../../docs/database/database-entities.md#7-payments--transfers), [03_database_tables.md](../../../docs/services/payment-service/03_database_tables.md)

---

## ERD (Entity Context)

```
PARENT_ORDERS (Order Svc)          TRANSACTIONS
+------------------------+         +-----------------------------------+
| id          BIGSERIAL PK| 1---1  | id                BIGSERIAL PK    |
| total_amt               |<------>| parent_order_id   BIGINT FK       |--+
| final_amt               |        | amount            DECIMAL         |  |
| status                  |        | trans_ref         VARCHAR         |  | 1:N
+------------------------+         | stripe_transfer_id VARCHAR        |  |
                                   | application_fee_amount DECIMAL    |  |
                                   | stripe_connect_mode VARCHAR       |  |
                                   | status            VARCHAR         |  |
                                   | raw_response      JSONB           |  |
                                   | pay_at            TIMESTAMP       |  |
                                   | created_at / updated_at           |  |
                                   +-----------------------------------+  |
                                                                          |
              +-----------------------------------------------------------+
              v
SELLER_TRANSFERS
+-------------------------------+
| transaction_id   BIGINT FK    |
+-------------------------------+
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `parent_order_id` | BIGINT | FK -> PARENT_ORDERS.id | Parent checkout order reference |
| 3 | `amount` | DECIMAL | -- | Total transaction amount (gross, before fees) |
| 4 | `trans_ref` | VARCHAR | -- | Stripe PaymentIntent ID (format: `pi_xxx`) |
| 5 | `stripe_transfer_id` | VARCHAR | -- | Stripe Transfer ID (`tr_xxx`), first transfer only |
| 6 | `application_fee_amount` | DECIMAL | -- | Platform total commission across all sub-orders |
| 7 | `stripe_connect_mode` | VARCHAR | -- | `DESTINATION` / `TRANSFER` / `NONE` |
| 8 | `status` | VARCHAR | -- | `SUCCESS` / `FAILED` / `REFUNDED` / `PARTIALLY_REFUNDED` |
| 9 | `raw_response` | JSONB | -- | Raw response payload from Stripe/payment gateway |
| 10 | `pay_at` | TIMESTAMP | -- | Timestamp when payment was confirmed by Stripe |
| 11 | `created_at` | TIMESTAMP | NOT NULL | Row creation timestamp |
| 12 | `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `pk_transactions` | `id` | PRIMARY KEY | Unique row identifier |
| `idx_transactions_parent_order` | `parent_order_id` | BTREE | Lookup by parent order |

---

## Status States

| Status | Meaning | Trigger |
|--------|---------|---------|
| `SUCCESS` | Payment completed | Stripe webhook `payment_intent.succeeded` |
| `FAILED` | Payment failed | Stripe webhook `payment_intent.payment_failed` |
| `REFUNDED` | Full amount returned to buyer | All refunds processed |
| `PARTIALLY_REFUNDED` | Partial amount returned | Some refunds processed |

---

## State Transitions

See [state-transaction.md](../../state-diagrams/payment-service/state-transaction.md)

---

## Business Rules

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-005 | Transaction created with status `PENDING` on `payment.requested` |
| BR-PAYMENT-006 | `trans_ref` stores the Stripe PaymentIntent ID for correlation |
| BR-PAYMENT-007 | `application_fee_amount` = sum of all `platform_commission_amt` from SELLER_TRANSFERS |
| BR-PAYMENT-008 | One PARENT_ORDER has exactly one TRANSACTION (1:1) |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| PARENT_ORDERS | 1:1 | `parent_order_id` FK |
| SELLER_TRANSFERS | 1:N | `transaction_id` in SELLER_TRANSFERS |
| REFUNDS | 1:N | `transaction_id` in REFUNDS |
