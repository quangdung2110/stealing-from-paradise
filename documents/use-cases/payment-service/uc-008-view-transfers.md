# UC-PAYMENT-008: View Transfers (Seller)

**Domain**: Payment Service  
**Actor**: Seller  
**Priority**: Medium  
**References**: [02_API_payment_service.md](../../../docs/services/payment-service/02_API_payment_service.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Seller is authenticated (JWT with SELLER role) |
| P2 | Seller has SELLER_STRIPE_ACCOUNTS record (may be PENDING) |

---

## Main Flow (Transfer History)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Seller | Calls GET `/seller/payments/transfers` with optional filters |
| 2 | System | Queries SELLER_TRANSFERS WHERE `seller_id` = authenticated seller (→ ENTITY-PAYMENT-003) |
| 3 | System | Applies optional filters: status, from_date, to_date |
| 4 | System | Paginates results (page, size) |
| 5 | System | Returns list of transfer records with status, amounts, dates |

---

## Main Flow (Balance)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Seller | Calls GET `/seller/payments/balance` |
| 2 | System | Aggregates SELLER_TRANSFERS by status (→ ENTITY-PAYMENT-003) |
| 3 | System | Calculates: `pending_balance` (RETURN_WINDOW + READY_FOR_PAYOUT), `available_balance` (PAID_OUT), `total_earned` (all non-refunded) |
| 4 | System | Returns `{ pending_balance, available_balance, total_earned }` |

---

## Main Flow (Stripe Dashboard)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Seller | Calls GET `/seller/payments/stripe-dashboard` |
| 2 | System | Looks up `express_dashboard_url` from SELLER_STRIPE_ACCOUNTS (→ ENTITY-PAYMENT-001) |
| 3 | System | If URL missing/expired, calls Stripe API to create new login link |
| 4 | System | Returns `{ dashboard_url }` |

---

## Main Flow (Earnings Overview)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Seller | Calls GET `/seller/payments/earnings` |
| 2 | System | Aggregates earnings summary: total revenue, total fees, net earnings |
| 3 | System | Returns earnings overview with transfer history |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | No Stripe account | Stripe dashboard endpoint returns 404 |
| A2 | No transfers yet | Transfer history returns empty list |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | Seller can see all transfer records with status and amounts |
| Q2 | Seller can access Stripe Express Dashboard for detailed financial views |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-009 | Platform commission reflected in net amounts |
| BR-PAYMENT-010 | Delayed payout visible in transfer status flow |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| UC-PAYMENT-001 | Onboard Stripe (required before seeing dashboard) |
| UC-PAYMENT-007 | Transfer to Seller (creates the records viewed here) |
