# ENTITY-REFUND-001: Refund

**Domain**: Refund Service
**Table**: `refund.refunds`
**Purpose**: Records buyer refund requests, admin review decisions, and Stripe refund processing.
**Last Updated**: 2026-05-12 (split from payment-service)

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `transaction_id` | BIGINT | NOT NULL | Parent payment transaction |
| 3 | `order_id` | BIGINT | NOT NULL | Sub-order being refunded |
| 4 | `user_id` | BIGINT | NULLABLE | Buyer who requested the refund |
| 5 | `group_ref` | UUID | NULLABLE | Groups multiple refunds from the same request |
| 6 | `type` | VARCHAR | NOT NULL | `FULL` / `PARTIAL` |
| 7 | `initiated_by` | VARCHAR | NOT NULL | `BUYER` / `SELLER` / `SYSTEM` |
| 8 | `refund_reason_type` | VARCHAR | NULLABLE | Reason category |
| 9 | `amount` | DECIMAL | NOT NULL | Total refund amount |
| 10 | `reason` | TEXT | NULLABLE | Buyer-provided refund reason |
| 11 | `status` | VARCHAR | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `SUCCESS` / `FAILED` / `REJECTED` |
| 12 | `evidence_images` | JSONB | NULLABLE | Array of MinIO image URLs |
| 13 | `reject_reason` | TEXT | NULLABLE | Admin-provided rejection reason |
| 14 | `admin_note` | TEXT | NULLABLE | Admin internal note |
| 15 | `reviewed_by` | BIGINT | NULLABLE | Admin who approved/rejected |
| 16 | `reviewed_at` | TIMESTAMP | NULLABLE | When admin reviewed |
| 17 | `refund_ref` | VARCHAR | NULLABLE | Stripe refund ID |
| 18 | `raw_response` | JSONB | NULLABLE | Raw Stripe refund response |
| 19 | `created_at` | TIMESTAMP | NOT NULL | Row creation timestamp |
| 20 | `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

---

## Status States

| Status | Meaning | Trigger |
|--------|---------|---------|
| `PENDING` | Awaiting admin review | Buyer submits refund request |
| `SUCCESS` | Refund processed via Stripe | Stripe refund API succeeds |
| `FAILED` | Stripe refund failed | Stripe refund API error |
| `REJECTED` | Admin denied refund | Admin reject action |

---

## Business Rules

See [refund-service/br-refund.md](../../business-rules/refund-service/br-refund.md)
