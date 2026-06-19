# UC-PAYMENT-001: Onboard Stripe Account

**Domain**: Payment Service  
**Actor**: Seller  
**Priority**: High  
**References**: [02_API_payment_service.md](../../../docs/services/payment-service/02_API_payment_service.md), [08_PAYMENT_ORDER_INTEGRATION.md](../../../docs/business/08_PAYMENT_ORDER_INTEGRATION.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Seller is authenticated (JWT with SELLER role) |
| P2 | Seller has an active SELLERS record |
| P3 | Seller does NOT have a Stripe account with `details_submitted = true` |

---

## Main Flow

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Seller | Calls POST `/stripe/onboarding/start` |
| 2 | System | Checks UNIQUE(seller_id): if existing account with `details_submitted = true`, return 409 (→ ENTITY-PAYMENT-001) |
| 3 | System | If no existing account, creates Stripe Express account via `Account.create()` |
| 4 | System | Inserts SELLER_STRIPE_ACCOUNTS row (status = PENDING) (→ ENTITY-PAYMENT-001) |
| 5 | System | Calls Stripe `accountLinks.create()` to generate onboarding URL |
| 6 | System | Sets `onboarding_url_expires_at = NOW() + 24h` (→ ENTITY-PAYMENT-001) |
| 7 | System | Returns 201 `{ onboarding_url, expires_at }` |
| 8 | Seller | Opens onboarding URL in browser, completes Stripe KYC |
| 9 | Stripe | Sends `account.updated` webhook |
| 10 | System | Updates `charges_enabled`, `payouts_enabled`, `details_submitted`; sets `account_status = ACTIVE` (→ ENTITY-PAYMENT-001) |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Seller already onboarded | Return 409 Conflict with message "Stripe account already verified" |
| A2 | Onboarding URL expired | Seller calls POST `/stripe/onboarding/refresh-link` |
| A3 | Stripe suspends account | Webhook sets `account_status = SUSPENDED`; publish `stripe.account_suspended` |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | SELLER_STRIPE_ACCOUNTS row exists with `stripe_account_id` populated |
| Q2 | `charges_enabled = true` AND `payouts_enabled = true` AND `details_submitted = true` |
| Q3 | Seller can receive transfers via Stripe Connect |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-001 | Seller must have charges_enabled before transfers |
| BR-PAYMENT-002 | Onboarding URL expires after 24h |
| BR-PAYMENT-003 | Duplicate account prevention via UNIQUE(seller_id) |
| BR-PAYMENT-004 | Webhook syncs account status from Stripe |
| BR-PAYMENT-005 | KYC required before receiving transfers |
| BR-PAYMENT-006 | Refresh link only if URL expired and KYC incomplete |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| UC-PAYMENT-003 | Handle Stripe Webhook (account.updated) |
| UC-PAYMENT-007 | Transfer to Seller (requires ACTIVE account) |
