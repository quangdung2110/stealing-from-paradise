# Kafka Request-Reply Pattern
**Verified against code:** 2026-06-16  
**Source:** `common-lib :: KafkaTopics`, `KafkaReplyService` (per producer service)

## Vì sao Request-Reply qua Kafka?
Các luồng dưới đây cần **kết quả đồng bộ-tính** từ service khác nhưng vẫn muốn **giữ loose coupling** (không gọi REST trực tiếp, không cần Eureka resolve runtime). Mỗi request có `correlation_id`; responder copy nguyên vào response, requester hoàn thành `CompletableFuture` đang chờ.

## Active Pairs

| # | Request | Response | Requester | Responder | Mục đích |
|---|---------|----------|-----------|-----------|---------|
| 1 | `order.address.request` | `order.address.response` | product / flashsale | identity | Validate & inflate địa chỉ giao hàng |
| 2 | `order.refunds.request` | `order.refunds.response` | order | refund | Đọc danh sách / chi tiết refund cho buyer / admin view |
| 3 | `order.refund_presigned_url.request` | `order.refund_presigned_url.response` | order | refund | Lấy presigned URL cho buyer upload ảnh evidence |
| 4 | `order.payment_status.request` | `order.payment_status.response` | order | refund | Kiểm tra trạng thái refund/payment cho refund view |
| 5 | `search.index_data.request` | `search.index_data.response` | search | product | Lấy snapshot product/category/SKU cho indexing |

## 1. Address Validation
**Dùng tại:** `product-service` checkout submit, `flashsale-service` buy flow.

Request:
```json
{ "correlation_id": "uuid", "user_id": 42, "address_id": 5 }
```
Response:
```json
{
  "correlation_id": "uuid",
  "addressId": 5,
  "userId": 42,
  "fullAddress": "123 Le Van Viet",
  "provinceId": 79,
  "districtId": 769,
  "error": false
}
```

## 2. Refund Reads
Order Service hỏi Refund Service các view refund.

Request:
```json
{
  "correlation_id": "uuid",
  "user_id": 42,
  "scope": "BUYER_ORDER" | "BUYER_PARENT" | "ADMIN_LIST" | "ADMIN_DETAIL",
  "order_id": 1001,
  "parent_order_id": 555,
  "refund_id": 777
}
```
Response envelope: `{ correlation_id, data: <refund-payload | list>, error }`.

## 3. Refund Presigned URL
Lấy upload URL MinIO cho ảnh chứng cứ trước khi tạo refund.

Request:
```json
{ "correlation_id": "uuid", "user_id": 42, "order_id": 1001, "filename": "evidence.jpg" }
```
Response:
```json
{ "correlation_id": "uuid", "presigned_url": "...", "object_key": "...", "expires_in": 300 }
```

## 4. Payment Status (for Refund Views)
Order hỏi Refund (service nắm trạng thái payment liên quan refund) trước khi render màn refund.

Request:
```json
{ "correlation_id": "uuid", "parent_order_id": 555 }
```
Response:
```json
{ "correlation_id": "uuid", "transactionStatus": "SUCCESS", "lastRefundStatus": "PENDING" }
```

## 5. Search Index Data
Search service yêu cầu product snapshot (cho event-driven sync hoặc full reindex).

Request:
```json
{
  "correlation_id": "uuid",
  "request_type": "PRODUCT_BY_ID" | "ACTIVE_PRODUCTS_PAGE" | "CATEGORY_BY_ID",
  "product_id": "uuid",
  "page": 0,
  "size": 500
}
```
Response:
```json
{ "correlation_id": "uuid", "docs": [ {sku_id, product_id, ...} ], "hasMore": true, "error": false }
```

## Implementation Notes
| Concern | Detail |
|---------|--------|
| Timeout | Mặc định **30s**; requester fail nếu không nhận `correlation_id` khớp |
| Correlation lookup | In-memory map của `correlation_id → CompletableFuture` trong `KafkaReplyService` |
| Partitioning | Tất cả pair dùng `correlation_id` làm key → cùng partition, ordering ổn định |
| Error propagation | Responder set `"error": true` + `"errorMessage"`; requester ném exception domain riêng |
| Retry | Không retry tự động — caller quyết định fallback (404 trả về client, hoặc DB read fallback) |

## Anti-patterns
- ❌ Dùng request-reply cho luồng có thể fire-and-forget (notification, indexing partial update).
- ❌ Dùng request-reply cho luồng chạy > 30s (chia thành event flow).
- ❌ Hard-code topic string ở producer — luôn import từ `KafkaTopics`.
