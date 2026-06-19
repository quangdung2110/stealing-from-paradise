# State Diagram: Stripe Account

**Stable ID:** `STATE-PAYMENT-003`
**Entity**: SELLER_STRIPE_ACCOUNTS (ENTITY-PAYMENT-001)  
**Domain**: Payment Service  
**References**: [entity-seller-stripe-account.md](../../data-models/payment-service/entity-seller-stripe-account.md), [02_API_payment_service.md](../../../docs/services/payment-service/02_API_payment_service.md)

---

## State Machine

```
                        [*]
                         |
                         | POST /stripe/onboarding/start
                         v
                      PENDING
                         |
                         | seller opens onboarding URL
                         v
                    IN_PROGRESS
                      /       \
                     /         \
          account.  /           \  account.updated
          updated  /             \ (suspension)
          (complete)              \
                  v               v
              COMPLETE        SUSPENDED
                  |               |
                  | restriction   | appeal
                  v               | success
             RESTRICTED           |
              /   \               |
             /     \              |
   more KYC/       \account.     |
   provided \       \updated     |
            v       v           |
         COMPLETE  SUSPENDED    |
                                |
                  +-------------+
                  |
                  v
                 [*]
              (terminal)
```

---

## State Transition Table

| From | To | Trigger | Actor | Cites |
|------|----|---------|-------|-------|
| `[*]` | `PENDING` | Seller calls POST /stripe/onboarding/start; Stripe Express account created | Seller / System | UC-PAYMENT-001 |
| `PENDING` | `IN_PROGRESS` | Seller opens onboarding URL, begins KYC | Seller | UC-PAYMENT-001 |
| `IN_PROGRESS` | `COMPLETE` | Stripe webhook `account.updated`: `details_submitted=true`, `charges_enabled=true`, `payouts_enabled=true` | Stripe / System | UC-PAYMENT-003 |
| `IN_PROGRESS` | `SUSPENDED` | Stripe webhook `account.updated`: account flagged/suspended | Stripe / System | UC-PAYMENT-003 |
| `COMPLETE` | `RESTRICTED` | Stripe requires additional verification (e.g., tax docs) | Stripe | UC-PAYMENT-003 |
| `RESTRICTED` | `COMPLETE` | Seller provides required docs; `account.updated` confirms | Seller / Stripe | UC-PAYMENT-003 |
| `RESTRICTED` | `SUSPENDED` | Escalation of restrictions to suspension | Stripe | UC-PAYMENT-003 |
| `SUSPENDED` | `COMPLETE` | Appeal successful; account reinstated | Stripe | UC-PAYMENT-003 |
| `SUSPENDED` | `[*]` | Account permanently closed | -- | -- |

---

## Field Values per State

| State | account_status | details_submitted | charges_enabled | payouts_enabled |
|-------|---------------|-------------------|-----------------|-----------------|
| PENDING | PENDING | false | false | false |
| IN_PROGRESS | PENDING | false | false | false |
| COMPLETE | ACTIVE | true | true | true |
| RESTRICTED | RESTRICTED | true | true/false | false |
| SUSPENDED | SUSPENDED | true | false | false |

---

## Kafka Events per Transition

| Transition | Kafka Topic |
|------------|-------------|
| IN_PROGRESS -> COMPLETE | (none needed; account is now operational) |
| IN_PROGRESS -> SUSPENDED | `stripe.account_suspended` |
| COMPLETE -> RESTRICTED | `seller.stripe_requirement` |
| RESTRICTED -> SUSPENDED | `stripe.account_suspended` |

---

## Guard Conditions

| Transition | Guard |
|------------|-------|
| [*] -> PENDING | Seller has no existing account with `details_submitted = true` |
| PENDING -> IN_PROGRESS | Seller accesses valid (non-expired) onboarding URL |
| IN_PROGRESS -> COMPLETE | `charges_enabled = true` AND `payouts_enabled = true` AND `details_submitted = true` |
| COMPLETE -> RESTRICTED | `payouts_enabled` becomes false while `details_submitted` remains true |

---

## Related States in Other Entities

| Entity | Related State | Relationship |
|--------|--------------|-------------|
| SELLER_TRANSFERS.status | SKIPPED | If account not COMPLETE, transfers are skipped (BR-PAYMENT-001) |
