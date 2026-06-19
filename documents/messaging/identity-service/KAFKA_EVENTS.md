# Kafka Events -- Identity Service

> Service: identity-service (Port 8081)
> Source: Backend code `com.flashsale.identityservice`
> Generated: 2026-05-10

---

## Events Consumed

### order.delivered (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | identity-service |
| **GroupId** | identity-service-order-group |
| **Action** | Unlock SELLER product posting capability if seller has completed at least 1 delivered order |

### order.cancelled (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | identity-service |
| **Action** | Audit log cancellation, potential re-enable logic |

### refund.admin_approved (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | identity-service |
| **Action** | Notify customer/seller, handle account status changes |

### refund.rejected (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | identity-service |
| **Action** | Push notification to buyer |

---

## Request-Reply (Identity Service is Responder)

### order.address -- Order <-> Identity

| Role | Service | Topic |
|------|---------|-------|
| Requester | Order Service | `order.address.request` |
| Responder | Identity Service | `order.address.response` |

**Request:**
```json
{ "correlation_id": "uuid", "address_id": 1, "user_id": 42 }
```

**Response (found):**
```json
{
  "correlation_id": "uuid",
  "address_id": 1,
  "user_id": 42,
  "full_address": "123 Nguyen Hue, District 1, HCMC",
  "province_id": 79,
  "district_id": 760,
  "error": false
}
```

**Response (not found):**
```json
{ "correlation_id": "uuid", "error": true }
```

---

## Events Produced

Identity Service does NOT produce Kafka domain events directly. All user state changes are persisted to PostgreSQL and exposed via REST API (`/v1/internal/users/*`). Account lifecycle events (`account.registered`, `account.login`, etc.) are produced by API Gateway or handled synchronously at the REST layer.
