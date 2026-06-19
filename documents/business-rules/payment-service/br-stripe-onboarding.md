# BR-PAYMENT: Stripe Onboarding Business Rules

**Domain**: Payment Service  
**Feature**: Stripe Connect Seller Onboarding  
**References**: [02_API_payment_service.md](../../../docs/services/payment-service/02_API_payment_service.md), [08_PAYMENT_ORDER_INTEGRATION.md](../../../docs/business/08_PAYMENT_ORDER_INTEGRATION.md)

---

## BR-PAYMENT-001: Charges Enabled Requirement

| Property | Value |
|----------|-------|
| **Rule** | Platform MUST NOT transfer funds to a seller whose `charges_enabled = false` |
| **Check** | Before executing Stripe Transfer, check `SELLER_STRIPE_ACCOUNTS.charges_enabled` |
| **On False** | Set `SELLER_TRANSFERS.status = SKIPPED` |
| **Cites** | UC-PAYMENT-007 |

---

## BR-PAYMENT-002: Onboarding URL Expiry

| Property | Value |
|----------|-------|
| **Rule** | Onboarding URL expires 24 hours after generation |
| **Expiry Column** | `SELLER_STRIPE_ACCOUNTS.onboarding_url_expires_at` = `created_at` + 24h |
| **Cleanup** | JOB-15 nullifies `onboarding_url` where `onboarding_url_expires_at < NOW()` |
| **Expired URL Behavior** | Seller receives 400 if URL expired; must call POST `/stripe/onboarding/refresh-link` |
| **Cites** | UC-PAYMENT-001 |

---

## BR-PAYMENT-003: Duplicate Account Prevention

| Property | Value |
|----------|-------|
| **Rule** | One seller MUST have exactly one Stripe Express account |
| **Enforcement** | UNIQUE constraint on `SELLER_STRIPE_ACCOUNTS.seller_id` |
| **On Duplicate** | POST `/stripe/onboarding/start` returns 409 Conflict if `details_submitted = true` |
| **Cites** | UC-PAYMENT-001 |

---

## BR-PAYMENT-004: Webhook Account Sync

| Property | Value |
|----------|-------|
| **Rule** | Stripe `account.updated` webhook MUST sync local state |
| **Synced Fields** | `charges_enabled`, `payouts_enabled`, `details_submitted` |
| **Status Mapping** | If `charges_enabled AND payouts_enabled AND details_submitted` -> `account_status = ACTIVE` |
| **Suspension** | If Stripe flags account -> `account_status = SUSPENDED`; publish `stripe.account_suspended` |
| **Additional KYC** | If Stripe requires more info -> `account_status = RESTRICTED`; publish `seller.stripe_requirement` |
| **Cites** | UC-PAYMENT-003 |

---

## BR-PAYMENT-005: KYC Requirements

| Property | Value |
|----------|-------|
| **Rule** | Seller must complete Stripe KYC before receiving any transfers |
| **Required Fields** | Country (default US), business type, tax ID, bank account, identity verification |
| **Incomplete KYC** | `details_submitted = false` -> no transfers possible |
| **Onboarding URL Refresh** | Allowed only if `details_submitted = false`; returns 409 otherwise |
| **Cites** | UC-PAYMENT-001 |

---

## BR-PAYMENT-006: Refresh Link Guard

| Property | Value |
|----------|-------|
| **Rule** | Refresh onboarding link only when current URL is expired AND KYC incomplete |
| **Condition** | `onboarding_url_expires_at < NOW()` AND `details_submitted = false` |
| **On Success** | New `onboarding_url` generated via Stripe `accountLinks.create()` |
| **On Failure** | If `details_submitted = true`, return 409 (already completed) |
| **Cites** | UC-PAYMENT-001 |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| STATE-PAYMENT-003 | [state-stripe-account.md](../../state-diagrams/payment-service/state-stripe-account.md) |
