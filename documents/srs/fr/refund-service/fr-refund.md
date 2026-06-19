# FR-REFUND: Functional Requirements

**Stable ID:** FR-REFUND
**Domain**: Refund Service  
**References**: [br-refund.md](../../../business-rules/refund-service/br-refund.md)

---

## FR-REFUND-001: Refund Eligibility Validation

| Property | Value |
|----------|-------|
| **Description** | System validates refund requests before processing |
| **Checks** | (1) Order within return_window_end; (2) Refund amount <= remaining balance; (3) Evidence images provided for BUYER_REQUEST |
| **On Violation** | Return error with specific reason |
| **Cites** | [UC-REFUND-001](../../../use-cases/refund-service/uc-001-create-refund.md), BR-REFUND-001, BR-REFUND-006 |

---

## FR-REFUND-002: Refund Evidence Upload

| Property | Value |
|----------|-------|
| **Description** | Buyer must upload evidence images when requesting a refund |
| **Field** | `evidence_images` (JSONB array of MinIO URLs) |
| **Requirement** | At least 1 image for BUYER_REQUEST type |
| **Storage** | Images uploaded to MinIO, URLs stored in REFUNDS.evidence_images |
| **Cites** | [UC-REFUND-001](../../../use-cases/refund-service/uc-001-create-refund.md), BR-REFUND-002 |

---

## FR-REFUND-003: Admin Refund Review

| Property | Value |
|----------|-------|
| **Description** | Admin reviews and either approves or rejects refund requests |
| **Approve Endpoint** | POST `/v1/admin/refunds/{id}/approve` |
| **Reject Endpoint** | POST `/v1/admin/refunds/{id}/reject` |
| **Auth** | JWT (ADMIN) |
| **Approve Action** | Set status = APPROVED, trigger Stripe refund, publish `refund.admin_approved` |
| **Reject Action** | Set status = REJECTED, set `reject_reason`, publish `refund.rejected` |
| **Cites** | [UC-REFUND-002](../../../use-cases/refund-service/uc-002-approve-refund.md), [UC-REFUND-003](../../../use-cases/refund-service/uc-003-reject-refund.md), BR-REFUND-003, BR-REFUND-009 |

---

## FR-REFUND-004: Stripe Refund Execution

| Property | Value |
|----------|-------|
| **Description** | Execute the actual refund via Stripe API after admin approval |
| **Pre-Payout** | Stripe `Refund.create()` from platform balance; no reversal needed |
| **Post-Payout** | Stripe Transfer reversal; `SELLER_TRANSFERS.status = REVERSED` |
| **Response** | Set `refund_ref` (Stripe refund ID `re_xxx`), `raw_response` (Stripe payload) |
| **On Stripe Error** | Set status = FAILED, log error |
| **Cites** | [UC-REFUND-002](../../../use-cases/refund-service/uc-002-approve-refund.md), BR-REFUND-004 |

---

## FR-REFUND-005: RTS Auto-Refund

| Property | Value |
|----------|-------|
| **Description** | Return-To-Sender orders automatically generate full refunds |
| **Trigger** | Kafka event `order.returned` from Order Service |
| **Actions** | Create REFUND (type=FULL, refund_reason_type=RETURN_TO_SENDER), check pre/post payout, execute Stripe refund, publish `refund.rts_completed` |
| **Cites** | [UC-REFUND-001](../../../use-cases/refund-service/uc-001-create-refund.md), BR-REFUND-005 |
