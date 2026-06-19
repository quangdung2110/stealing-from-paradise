# Business Flows — Index
**Verified against code:** 2026-06-16

This folder documents the **business flows actually implemented in code**. Each flow file has the same structure:

| Section | Content |
|---------|---------|
| **1. Mục đích** | Statement of the business goal of this flow |
| **2. Actors & Trigger** | Who initiates and under what condition |
| **3. Public Endpoints** | REST endpoints exposed (path + method + handler) |
| **4. Kafka Topics** | Topics produced (→) and consumed (←) by this flow |
| **5. Sequence Diagram** | Mermaid `sequenceDiagram` for the happy path + branches |
| **6. State Transitions** | Mermaid `stateDiagram-v2` where the flow is stateful |
| **7. Implementation Map** | Use-case → Java class & method, with line numbers |
| **8. Notes & Caveats** | Known intentional deviations from older docs |

> **Convention:** "verified" means a `grep` against the current Java source confirms the handler exists at (or near) the cited line. Minor line drift (±5) is tolerated; structural changes are reflected.

## Catalog

| Domain | File | Primary service |
|--------|------|-----------------|
| Identity | [identity-service/flow-identity-access-profile.md](identity-service/flow-identity-access-profile.md) | identity-service |
| Catalog & Cart | [product-service/flow-product-catalog-cart-review.md](product-service/flow-product-catalog-cart-review.md) | product-service |
| Order lifecycle | [order-service/flow-order-lifecycle.md](order-service/flow-order-lifecycle.md) | order-service |
| Flash sale | [flashsale-service/flow-flashsale-session-purchase.md](flashsale-service/flow-flashsale-session-purchase.md) | flashsale-service |
| Payment + payout | [payment-service/flow-payment-stripe-payout.md](payment-service/flow-payment-stripe-payout.md) | payment-service |
| Refund admin review | [refund-service/flow-refund-admin-review.md](refund-service/flow-refund-admin-review.md) | refund-service |
| Search | [search-service/flow-search-indexing.md](search-service/flow-search-indexing.md) | search-service |
| Notifications | [notification-service/flow-notification-stream.md](notification-service/flow-notification-stream.md) | notification-service |
| AI chat | [ai-chat-service/flow-ai-chat-confirmation.md](ai-chat-service/flow-ai-chat-confirmation.md) | chat-service |
| Cross — order cancellation | [cross-service/flow-order-cancellation.md](cross-service/flow-order-cancellation.md) | order + product + payment + refund |
| Cross — refund processing | [cross-service/flow-refund-processing.md](cross-service/flow-refund-processing.md) | order + refund + payment |
| Cross — Stripe onboarding | [cross-service/flow-stripe-onboarding.md](cross-service/flow-stripe-onboarding.md) | payment + Stripe |

## Conventions used across flow docs

- **Topic constants** come from `common-lib :: KafkaTopics` (`REFUND_REQUESTED`, `PAYMENT_REQUESTED`, etc.); the lowercase names in tables are the actual topic strings.
- **Sequence diagrams use the actor → service → bus pattern.** Kafka is rendered as a participant when it carries the message between services.
- **Gateway prefix:** publicly the API gateway exposes routes under `/api/v1/...`; inside each service the same handler is annotated `/v1/...`. Tables in this folder list the **service-internal** path unless explicitly stated.
- **Status of each UC:** every flow has an "Implementation Map" table — if a use case in `documents/use-cases/` does not appear here, it is not yet implemented in code.
