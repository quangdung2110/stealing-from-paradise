# Flow: Flash Sale Session, Registration & Purchase
**Primary service:** `flashsale-service`  
**Verified against code:** 2026-06-16

## 1. Mục đích
Quản lý **phiên flash sale** (UPCOMING / ACTIVE / ENDED), việc **seller đăng ký sản phẩm** vào phiên, và **buyer mua flash** với decrement nguyên tử trên Redis. Đồng bộ giá flash sang `product-service` và `search-service` qua Kafka.

## 2. Actors & Trigger
| Actor | Hành động |
|-------|----------|
| Admin | Tạo / cập nhật / xóa phiên |
| Seller | Đăng ký item vào phiên (auto-approve) |
| Buyer | Xem phiên active, mua flash |
| Scheduler | Kích hoạt / kết thúc phiên theo timer |

## 3. Public Endpoints (service-internal `/v1/flash-sales`)
| Method | Path | Handler |
|--------|------|---------|
| GET | `/` | `FlashSaleController.getSessions` (L24) |
| GET | `/active` | `getActiveSessions` (L31) |
| GET | `/{sessionId}` | `getSessionDetail` (L37) |
| POST | `/` | `createSession` (L44) |
| PUT / DELETE | `/{sessionId}` | `updateSession` (L52) / `deleteSession` (L61) |
| POST | `/{sessionId}/buy` | `buyFlashSale` (L69) |
| POST | `/{sessionId}/items` | `createFlashSaleItem` (L79) |
| POST | `/{sessionId}/items/{itemId}/approve` / `/reject` | `approveItem` / `rejectItem` (L89, L99) |
| POST / DELETE | `/{sessionId}/reminders` | Reminder subscribe / unsubscribe (L109, L118) |

## 4. Kafka Topics
| Direction | Topic | Notes |
|-----------|-------|-------|
| → produce | `flash_sale.session_created` | On create |
| → produce | `flash_sale.session_started` / `flash_sale.session_ended` | Scheduler tick — payload includes `flashItems[]{sku_code, flash_price, flash_stock}` |
| → produce | `flash_sale.item_registered` | Seller registers (auto-approved) |
| → produce | `flash_sale.item_approved` / `flash_sale.item_rejected` | Admin overrides (kept for legacy compat) |
| → produce | `order.address_request` | Resolve buyer address |
| ← consume | `order.address_response` | Reply from identity |
| → produce | `order.checkout_submitted` | Buy → drive into order flow |

## 5. Sequence Diagram
```mermaid
sequenceDiagram
    actor Admin
    actor Seller
    actor Buyer
    participant FS as flashsale-service
    participant R as Redis
    participant K as Kafka
    participant ID as identity-service
    participant PR as product-service
    participant OR as order-service
    participant SR as search-service

    rect rgb(245,250,255)
    Note over Admin,FS: Phase 1 — setup
    Admin->>FS: POST /v1/flash-sales
    FS->>R: ZADD flash_sale:triggers (start/end)
    FS->>K: flash_sale.session_created
    Seller->>FS: POST /v1/flash-sales/{sid}/items
    FS->>FS: persist APPROVED item
    FS->>K: flash_sale.item_registered
    end

    rect rgb(248,255,248)
    Note over FS,SR: Phase 2 — lifecycle
    FS->>FS: FlashSaleSessionScheduler tick
    FS->>K: flash_sale.session_started (flashItems[])
    K->>PR: FlashSaleEventHandler apply flash prices
    K->>SR: refresh visible pricing in skus index
    end

    rect rgb(255,250,235)
    Note over Buyer,OR: Phase 3 — buy
    Buyer->>FS: GET /v1/flash-sales/active
    Buyer->>FS: POST /v1/flash-sales/{sid}/buy
    FS->>R: Lua DECRBY fs:stock:{itemId} (atomic)
    alt còn hàng
        FS->>K: order.address_request
        K->>ID: AddressKafkaConsumer
        ID->>K: order.address_response
        K->>FS: onAddressResponse
        FS->>K: order.checkout_submitted
        K->>OR: create order from flash purchase
    else hết hàng
        FS->>R: INCRBY rollback
        FS-->>Buyer: 409 SOLD_OUT
    end
    end

    rect rgb(250,240,240)
    Note over FS,SR: Phase 4 — end
    FS->>K: flash_sale.session_ended (flashItems[])
    K->>PR: reset prices to original
    K->>SR: refresh index
    end
```

## 6. State Transitions — `fs_sessions.status`
```mermaid
stateDiagram-v2
    [*] --> UPCOMING : admin create
    UPCOMING --> ACTIVE : scheduler at start_time
    ACTIVE --> ENDED : scheduler at end_time
    UPCOMING --> [*] : admin delete (soft)
    ENDED --> [*]
```

## 7. Implementation Map
| UC | Code reference |
|----|----------------|
| UC-FLASHSALE-001 Admin Create Session | `FlashSaleController.createSession` (L44), `FlashSaleService.createSession` (~L166) |
| UC-FLASHSALE-002 Seller Register Product | `createFlashSaleItem` (L79), service (~L127); auto-approved |
| UC-FLASHSALE-003 View Sessions | `getSessions` / `getActiveSessions` / `getSessionDetail` |
| UC-FLASHSALE-004 Buyer Buy | `buyFlashSale` (L69) + Lua decrement script |
| UC-FLASHSALE-006 System End Session | `FlashSaleSessionScheduler` (~L39); payload includes `flashItems[]` at ~L92 |

## 8. Notes & Caveats
- **Stock model:** flash stock kept in Redis (`fs:stock:{itemId}`) — DB row is the canonical reference but live decrement is Redis.
- **Auto-approve:** seller items become `APPROVED` immediately; admin approve/reject endpoints stay for legacy/manual cases.
- **Price sync payload** keeps backward-compatible `flashPriceMap` field for older product-service consumers.
- **Reminder endpoints** exist in code but are not in the active UC catalog.
- **Reactive stack:** `flashsale-service` uses WebFlux + R2DBC; do not mix with blocking JPA helpers.
