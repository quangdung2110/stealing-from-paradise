# ENTITY-REFUND-002: RefundItem

**Stable ID:** ENTITY-REFUND-002
**Domain**: Refund Service
**Table**: `refund.refund_items`
**Purpose**: Records individual items and quantities that are part of a refund request.
**Last Updated**: 2026-05-12

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `refund_id` | BIGINT | FK → refund.refunds.id, NOT NULL | Parent refund record |
| 3 | `item_id` | BIGINT | FK → orders.order_items.id, NOT NULL | The order line item being refunded |
| 4 | `quantity` | INTEGER | NOT NULL | Quantity being refunded from this item |
| 5 | `refund_amount` | DECIMAL | NULLABLE | Amount refunded for this specific item |
| 6 | `item_reason` | TEXT | NULLABLE | Reason specific to this item |
| 7 | `status` | VARCHAR | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `SUCCESS` / `FAILED` |
| 8 | `return_tracking_number` | VARCHAR | NULLABLE | Return shipment tracking number |
| 9 | `return_evidence_images` | JSONB | NULLABLE | Array of MinIO image URLs as evidence |
| 10 | `returned_at` | TIMESTAMP | NULLABLE | When seller confirmed receipt of returned goods |

---

## Business Rules

| Rule ID | Description |
|---------|-------------|
| BR-REFUND-006 | Sum of all `refund_items.refund_amount` for a refund must equal parent `refunds.amount`. |
| BR-REFUND-007 | Multiple items refunded in one request share a common `group_ref` UUID. |
| BR-REFUND-009 | Return tracking number is mandatory for physical returns (defective, failed delivery, RTS). |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| ENTITY-REFUND-001 (Refund) | N:1 | `refund_id` FK |
| ENTITY-ORDER-003 (OrderItem) | N:1 | `item_id` FK |
