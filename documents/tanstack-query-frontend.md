# Tích Hợp TanStack Query (React Query v5) — Kiến Trúc Frontend

> Tài liệu chi tiết về cơ chế quản lý trạng thái server (Server State), cấu hình bộ nhớ đệm (Cache), cơ chế đồng bộ và danh sách các Query/Mutation trong 3 ứng dụng frontend (`customer`, `seller`, `admin`).
> Cập nhật: 13/06/2026.

---

## 1. Tổng Quan về TanStack Query trong Dự Án

Hệ thống sử dụng **TanStack React Query v5** (`@tanstack/react-query`) làm thư viện quản lý trạng thái server (Server-State Management). Thư viện này giúp giảm tải yêu cầu mạng trùng lặp, tự động hóa việc lưu bộ nhớ đệm (caching), đồng bộ dữ liệu nền (background refetching), phân trang (pagination) và xử lý cập nhật giao diện ngay lập tức (optimistic updates).

### 1.1 Vị Trí và Cài Đặt
* Cấu hình toàn cục được định nghĩa ở package dùng chung: [queryClient.ts](file:///D:/dev/cc113/stealing-from-paradise/frontend/shared/lib/queryClient.ts)
* `@tanstack/react-query` được khai báo dưới dạng `peerDependencies` trong [package.json](file:///D:/dev/cc113/stealing-from-paradise/frontend/shared/package.json) của tầng `shared` để đảm bảo cả 3 ứng dụng sử dụng chung một phiên bản duy nhất, tránh lỗi trùng ngữ cảnh React.

### 1.2 Mối Liên Kết với Axios (`apiClient`)
Các hook của TanStack Query sử dụng `apiClient` từ [axios.ts](file:///D:/dev/cc113/stealing-from-paradise/frontend/shared/lib/axios.ts) để thực thi request HTTP.
* Khi API trả về lỗi `401 Unauthorized` do hết hạn access token, Axios Interceptor sẽ tự động thực hiện luồng refresh token (gọi `/auth/refresh` bằng `refreshToken`).
* Trong thời gian refresh, các truy vấn TanStack Query song song khác sẽ được đưa vào hàng đợi `failedQueue` và tự động gửi lại sau khi token mới được nạp thành công, giúp quá trình trải nghiệm người dùng không bị gián đoạn.

---

## 2. Cấu Hình Client Toàn Cục (`shared/lib/queryClient.ts`)

Hàm khởi tạo `createQueryClient` cấu hình các tùy chọn mặc định cho toàn bộ hệ thống:

```typescript
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export { QueryClientProvider };

export const createQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 1000 * 60,         // Dữ liệu được coi là mới (fresh) trong 1 phút
        refetchOnWindowFocus: false,  // Không tự động tải lại khi người dùng đổi tab trình duyệt
        retry: 1,                     // Thử lại tối đa 1 lần nếu request GET thất bại
        refetchOnMount: false,        // Không refetch lại khi component mount lại nếu cache chưa quá hạn
      },
      mutations: {
        retry: 0,                     // Không thử lại đối với các tác vụ thay đổi dữ liệu (POST/PUT/DELETE)
      },
    },
  });
```

> [!NOTE]
> Việc cấu hình `staleTime: 1 phút` và `refetchOnMount: false` giúp tối ưu hóa hiệu năng và số lượng request tới backend microservices khi người dùng chuyển đổi qua lại giữa các trang trong ứng dụng.

---

## 3. Các Chiến Thuật Polling và Retry Đặc Biệt (Advanced Polling Strategies)

Hệ thống tận dụng triệt để thuộc tính `refetchInterval` và `retry` của TanStack Query để thực hiện cơ chế cập nhật trạng thái tự động một cách tối ưu nhất:

### 3.1 Polling Stripe Client Secret
* **Trang:** [CheckoutPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/CheckoutPage.tsx)
* **Cơ chế:** Khi người mua chọn cổng thanh toán trực tuyến, frontend cần lấy Stripe `clientSecret` qua API `paymentApi.getClientSecret`. Nếu backend đang khởi tạo PaymentIntent với Stripe, API có thể trả về `404 Not Found` (chưa sẵn sàng).
* **Chiến thuật:** Sử dụng file helper [paymentClientSecretQuery.ts](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/paymentClientSecretQuery.ts):
  * `retry`: Thử lại tối đa **45 lần** (`CLIENT_SECRET_NOT_READY_RETRY_LIMIT = 45`) với khoảng cách 1 giây mỗi lần nếu lỗi trả về là 404 (Stripe chưa sẵn sàng). Với các lỗi client khác (400, 403), không thử lại.
  * `refetchInterval`: Tiếp tục poll mỗi 1000ms nếu dữ liệu trả về trống và chưa có lỗi cho đến khi nhận được `clientSecret`.

### 3.2 Polling Trạng Thái Thanh Toán
* **Trang:** [CheckoutResultPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/CheckoutResultPage.tsx)
* **Cơ chế:** Sau khi thanh toán qua Stripe, trang kết quả cần kiểm tra trạng thái giao dịch trên backend qua webhook xử lý bất đồng bộ.
* **Chiến thuật:** Query key `['payment', parentOrderId]` sẽ poll định kỳ mỗi **2000ms** (2 giây) và chỉ dừng lại khi trạng thái thanh toán chuyển sang `SUCCESS` hoặc `FAILED`.

### 3.3 Polling Trạng Thái Đơn Hàng COD
* **Trang:** [CheckoutResultPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/CheckoutResultPage.tsx)
* **Cơ chế:** Đối với đơn hàng COD (Thanh toán khi nhận hàng).
* **Chiến thuật:** Query key `['parent-order-status', parentOrderId]` sẽ poll mỗi **8000ms** (8 giây) và tự động dừng lại khi trạng thái đơn hàng thay đổi khác `PENDING` (ví dụ sang `CONFIRMED` hoặc `CANCELLED`).

### 3.4 Polling Đơn Hàng Đang Hoạt Động
* **Trang:** [OrderHistoryPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/OrderHistoryPage.tsx)
* **Cơ chế:** Trang lịch sử đơn hàng của người mua cần cập nhật trạng thái vận chuyển theo thời gian thực.
* **Chiến thuật:** Thay vì poll vô điều kiện, hàm `refetchInterval` sẽ duyệt qua danh sách đơn hàng hiện tại:
  * Nếu danh sách chứa bất kỳ đơn hàng nào có trạng thái hoạt động (`'PENDING'`, `'PAID'`, `'SHIPPING'`), hệ thống sẽ poll mỗi **10000ms** (10 giây).
  * Nếu tất cả đơn hàng đã ở trạng thái cuối cùng (`'DELIVERED'`, `'CANCELLED'`, `'REFUNDED'`), hệ thống sẽ dừng hoàn toàn việc tự động làm mới (`return false`).

### 3.5 Polling Onboarding Stripe
* **Trang:** [StripeOnboardingPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/pages/StripeOnboardingPage.tsx)
* **Cơ chế:** Khi seller hoàn thành KYC trên Stripe và quay lại app (URL có `?from=stripe`).
* **Chiến thuật:** Query key `['stripe-onboarding-status']` sẽ tự động poll mỗi **3000ms** (3 giây) và dừng lại ngay khi trạng thái tài khoản chuyển sang `COMPLETE`.

---

## 4. Danh Mục Query & Mutation Theo Từng Ứng Dụng

Mỗi ứng dụng sẽ gọi hàm `createQueryClient()` và bọc toàn bộ ứng dụng trong `<QueryClientProvider client={queryClient}>`. Dưới đây là danh sách chi tiết các Query Keys và Mutation của từng ứng dụng.

### 4.1 Ứng Dụng Người Mua (`customer-app`)

Ứng dụng quản lý mua sắm, hiển thị danh mục sản phẩm, đặt hàng, quản lý địa chỉ, hoàn tiền và AI chat.

#### A. Địa Chỉ
* **File liên quan:** [AddressPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/AddressPage.tsx)
* **Query:**
  * Query Key: `['user-addresses']`
  * API Endpoint: `GET /api/v1/users/me/addresses` (gọi [address.api.ts](file:///D:/dev/cc113/stealing-from-paradise/frontend/shared/api/address.api.ts))
* **Mutations:**
  * Thêm địa chỉ mới (`POST /api/v1/users/me/addresses`)
  * Cập nhật địa chỉ (`PUT /api/v1/users/me/addresses/{id}`)
  * Xóa địa chỉ (`DELETE /api/v1/users/me/addresses/{id}`)
  * *Cơ chế Invalidation:* Khi mutation thành công, hệ thống gọi `queryClient.invalidateQueries({ queryKey: ['user-addresses'] })` để làm mới danh sách địa chỉ.

#### B. Sản Phẩm
* **File liên quan:** [ProductListPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/ProductListPage.tsx), [ProductDetailPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/ProductDetailPage.tsx)
* **Query:**
  * Query Key: `['products', selectedCategory, page, searchQuery, sort, priceRange]`
    * API Endpoint: `GET /api/v1/products` (hoặc gọi Elasticsearch thông qua search-service `/api/v1/search/products`)
  * Query Key: `['product', id]`
    * API Endpoint: `GET /api/v1/products/{id}` (lấy thông tin chi tiết và danh sách các variants)

#### C. Giỏ Hàng & Thanh Toán
* **File liên quan:** [CheckoutPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/CheckoutPage.tsx), [CheckoutResultPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/CheckoutResultPage.tsx), [OrderReviewPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/OrderReviewPage.tsx)
* **Query:**
  * Query Key: `['addresses']` - Lấy danh sách địa chỉ nhận hàng của người dùng hiện tại khi checkout.
  * Query Key: `['parent-order', parentOrderId]` - Lấy thông tin đơn hàng tổng (parent order) vừa tạo.
  * Query Key: `['client-secret', parentOrderId]` - Lấy Stripe PaymentIntent Client Secret để khởi tạo component Stripe Elements.
  * Query Key: `['payment', parentOrderId]` - Truy vấn trạng thái thanh toán từ Stripe.
  * Query Key: `['parent-order-status', parentOrderId]` - Polling liên tục trạng thái đơn hàng từ order-service (cho đến khi chuyển từ `PENDING` sang `PAID` hoặc `FAILED`).

#### D. Đơn Hàng & Hoàn Tiền
* **File liên quan:** [OrderHistoryPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/OrderHistoryPage.tsx), [OrderDetailPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/OrderDetailPage.tsx), [RefundHistoryPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/RefundHistoryPage.tsx)
* **Query:**
  * Query Key: `['buyer-orders', filter, page]` - Danh sách đơn hàng có phân trang và lọc theo trạng thái đơn.
  * Query Key: `['my-refunds', filter, page]` - Danh sách các yêu cầu hoàn tiền.
* **Mutations:**
  * Hủy đơn hàng (`POST /api/v1/orders/parent/{id}/cancel` -> Invalidates `['parent-order', id]`)
  * Yêu cầu hoàn tiền (`POST /api/v1/orders/{orderId}/refunds` -> Invalidates `['parent-order', id]`)

#### E. Flash Sale
* **File liên quan:** [FlashSalePage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/FlashSalePage.tsx)
* **Query:**
  * Query Key: `['flash-sale-sessions']` - Danh sách toàn bộ phiên Flash Sale (đã kết thúc, đang diễn ra, sắp diễn ra).
  * Query Key: `['flash-sale-session', activeSessionId]` - Danh sách các sản phẩm đang/sắp giảm giá thuộc một phiên cụ thể.

#### F. Cấu Hình Tài Khoản
* **File liên quan:** [AccountSettingsPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/AccountSettingsPage.tsx), [ProfilePage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/customer/src/pages/ProfilePage.tsx)
* **Query:**
  * Query Key: `['user-profile']` - Lấy thông tin hồ sơ cá nhân.
  * Query Key: `['notification-preferences']` - Cấu hình nhận thông báo (email, SMS, in-app).
* **Mutations:**
  * Cập nhật hồ sơ -> Invalidates `['user-profile']`
  * Cập nhật tùy chọn thông báo -> Invalidates `['notification-preferences']`
  * Đăng ký tài khoản Seller (`POST /api/v1/users/register-seller` -> Invalidates `['user-profile']`)

---

### 4.2 Ứng Dụng Người Bán (`seller-app`)

Quản lý thông tin gian hàng, sản phẩm của shop, đơn hàng, doanh thu và tích hợp Stripe Connect.

#### A. Quản Lý Sản Phẩm
* **File liên quan:** [ProductManagementPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/pages/ProductManagementPage.tsx), [ProductFormModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/components/ProductManagement/ProductFormModal.tsx), [InventoryPanel.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/components/ProductManagement/InventoryPanel.tsx)
* **Query:**
  * Query Key: `['seller-products', statusFilter, page, debouncedSearch]` - Phân trang danh sách sản phẩm thuộc seller hiện tại.
  * Query Key: `['categories']` - Danh sách ngành hàng để hiển thị trong Product Form.
  * Query Key: `['seller-variants', productId]` - Danh sách các phiên bản (variants) và giá của sản phẩm.
  * Query Key: `['inventory-logs', variantId]` - Lịch sử điều chỉnh kho của biến thể sản phẩm.
* **Mutations:**
  * Thêm/Sửa sản phẩm -> Invalidates `['seller-products']`
  * Thêm/Sửa variants ([VariantModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/components/ProductManagement/VariantModal.tsx) -> Invalidates `['seller-variants', productId]`)
  * Gửi kiểm duyệt (Submit for review) -> Invalidates `['seller-products']`
  * Đăng bán (Publish product) -> Invalidates `['seller-products']`
  * Tạm ngưng bán (Unpublish product) -> Invalidates `['seller-products']`
  * Xóa sản phẩm -> Invalidates `['seller-products']`
  * Điều chỉnh tồn kho (`POST /api/v1/inventories/adjust` -> Invalidates `['inventory-logs', variantId]` và `['seller-products']`)

#### B. Đơn Hàng & Giao Nhận
* **File liên quan:** [SellerOrdersPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/pages/SellerOrdersPage.tsx), [SellerOrderDetailPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/pages/SellerOrderDetailPage.tsx)
* **Query:**
  * Query Key: `['seller-orders', filter, page]` - Danh sách các đơn hàng của shop theo trạng thái (chờ xác nhận, đang đóng gói, đang giao, đã hoàn thành, đã hủy).
  * Query Key: `['seller-order', orderId]` - Chi tiết một đơn hàng cụ thể.
  * Query Key: `['payment-for-seller', order?.parentOrderId]` - Chi tiết thanh toán / hoa hồng nền tảng của đơn hàng.
* **Mutations (thông qua [TrackingModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/components/Orders/TrackingModal.tsx), [CancelOrderModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/components/Orders/CancelOrderModal.tsx), [RTSModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/components/Orders/RTSModal.tsx)):**
  * Ready To Ship (Xác nhận đơn và chuyển sang đóng gói) -> Invalidates `['seller-orders']` và `['seller-order', orderId]`
  * Cập nhật mã vận đơn (Ship/Track order) -> Invalidates `['seller-orders']` và `['seller-order', orderId]`
  * Hủy đơn hàng từ phía Seller -> Invalidates `['seller-orders']` và `['seller-order', orderId]`

#### C. Doanh Thu & Stripe Connect
* **File liên quan:** [SellerPaymentsPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/pages/SellerPaymentsPage.tsx), [StripeOnboardingPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/pages/StripeOnboardingPage.tsx)
* **Query:**
  * Query Key: `['seller-dashboard-stats']` - Thống kê doanh thu, số lượng đơn hàng, tỷ lệ hủy của shop.
  * Query Key: `['seller-earnings']` - Lịch sử giao dịch tiền về tài khoản, số dư khả dụng và các giao dịch chuyển tiền (Stripe Transfers).
  * Query Key: `['stripe-onboarding-status']` - Kiểm tra trạng thái liên kết Stripe Connect của Seller (ACTIVE, PENDING, REQUIREMENTS_DUE).
* **Mutations:**
  * Khởi tạo liên kết Onboarding Stripe (`POST /api/v1/stripe/onboarding` -> Trả về URL để redirect sang Stripe Hosted Onboarding)
  * Lấy link dashboard Stripe Express (`POST /api/v1/stripe/dashboard` -> Mở tab mới xem thông tin thanh toán từ Stripe).

---

### 4.3 Ứng Dụng Quản Trị (`admin-app`)

Quản trị hệ thống, phê duyệt sản phẩm của Seller, phê duyệt yêu cầu hoàn tiền, cấu hình Flash Sale và quản lý người dùng.

#### A. Tổng Quan & Chỉ Số
* **File liên quan:** [AdminDashboard.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/pages/AdminDashboard.tsx)
* **Query:** Sử dụng `useQueries` tải song song 4 chỉ số:
  * Query Key: `['admin-dash-users']` - Số lượng người dùng hệ thống.
  * Query Key: `['admin-dash-pending-products']` - Số sản phẩm đang chờ duyệt.
  * Query Key: `['admin-dash-pending-refunds']` - Số yêu cầu hoàn tiền chưa giải quyết.
  * Query Key: `['admin-dash-active-flash-sales']` - Số phiên Flash Sale đang hoạt động.

#### B. Phê Duyệt Sản Phẩm
* **File liên quan:** [ProductModerationPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/pages/ProductModerationPage.tsx)
* **Query:**
  * Query Key: `['admin-pending-products', tab, page]` - Danh sách sản phẩm chờ kiểm duyệt.
* **Mutations (thông qua [RejectProductModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/components/ProductModeration/RejectProductModal.tsx)):**
  * Phê duyệt sản phẩm (`POST /api/v1/admin/products/{id}/approve` -> Invalidates `['admin-pending-products']`)
  * Từ chối sản phẩm kèm lý do (`POST /api/v1/admin/products/{id}/reject` -> Invalidates `['admin-pending-products']`)

#### C. Phê Duyệt Hoàn Tiền
* **File liên quan:** [RefundsPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/pages/RefundsPage.tsx)
* **Query:**
  * Query Key: `['admin-refunds', statusFilter, typeFilter, page]` - Danh sách các yêu cầu hoàn tiền.
* **Mutations (thông qua [ApproveRefundModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/components/Refunds/ApproveRefundModal.tsx), [RejectRefundModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/components/Refunds/RejectRefundModal.tsx)):**
  * Duyệt hoàn tiền (`POST /api/v1/admin/refunds/{refundId}/approve` -> Trigger hoàn tiền thật qua Stripe Connect và cập nhật DB -> Invalidates `['admin-refunds']`)
  * Từ chối hoàn tiền (`POST /api/v1/admin/refunds/{refundId}/reject` -> Invalidates `['admin-refunds']`)

#### D. Cấu Hình Phiên Flash Sale
* **File liên quan:** [FlashSaleConfigPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/pages/FlashSaleConfigPage.tsx)
* **Query:**
  * Query Key: `['admin-flash-sale-sessions']` - Danh sách tất cả phiên Flash Sale.
* **Mutations:**
  * Tạo phiên mới (`POST /api/v1/flash-sales`)
  * Kích hoạt phiên (`PUT /api/v1/flash-sales/{id}/status`)
  * Xóa phiên (`DELETE /api/v1/flash-sales/{id}`)
  * *Cơ chế Invalidation:* Sau khi thao tác, gọi `invalidateQueries({ queryKey: ['admin-flash-sale-sessions'] })`.

#### E. Quản Lý Tài Khoản & Stripe Connect
* **File liên quan:** [UserManagementPage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/pages/UserManagementPage.tsx), [SellerStripePage.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/pages/SellerStripePage.tsx)
* **Query:**
  * Query Key: `['admin-users', roleFilter, statusFilter, page]` - Quản lý danh sách người dùng.
  * Query Key: `['admin-seller-stripe-accounts']` - Quản lý tài khoản Stripe Connect của các shop.
* **Mutations (thông qua [BanUserModal.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/admin/src/components/UserManagement/BanUserModal.tsx)):**
  * Khóa/mở khóa tài khoản (Ban/Unban user -> Invalidates `['admin-users']`).

---

## 5. Cơ Chế Mocking Dữ Liệu & Kiểm Thử (Testing Setup)

### 5.1 Mocking trong Unit/Integration Tests
Các bài kiểm thử component và pages trong dự án cần giả lập hành vi mạng và cache của React Query.
* **Mock Setup:** Dự án định nghĩa file helper [utils.tsx](file:///D:/dev/cc113/stealing-from-paradise/frontend/apps/seller/src/test/utils.tsx) cung cấp hàm `renderWithProviders`:
  ```typescript
  import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
  import { render } from '@testing-library/react';

  export function renderWithProviders(ui: React.ReactElement) {
    const testQueryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, staleTime: Infinity },
        mutations: { retry: false },
      },
    });

    return {
      ...render(
        <QueryClientProvider client={testQueryClient}>
          {ui}
        </QueryClientProvider>
      ),
      queryClient: testQueryClient,
    };
  }
  ```
* Việc vô hiệu hóa `retry` và đặt `staleTime: Infinity` trong môi trường kiểm thử giúp ngăn chặn các request ngầm chạy nền làm sai lệch kết quả khẳng định (assertions) của test.

---

## 6. Các Quy Tắc Thiết Kế (Best Practices) Áp Dụng Trong Dự Án

### 6.1 Khai Báo Query Keys Rõ Ràng & Nhất Quán
Query Keys phải luôn được khai báo dưới dạng mảng (Array). Thành phần đầu tiên luôn là domain string (ví dụ: `'products'`, `'seller-orders'`), theo sau là các biến số ảnh hưởng trực tiếp tới dữ liệu như bộ lọc, từ khóa tìm kiếm và trang hiện tại:

```typescript
// Đúng quy tắc:
useQuery({
  queryKey: ['seller-products', statusFilter, page, debouncedSearch],
  queryFn: () => getSellerProducts({ status: statusFilter, page, search: debouncedSearch }),
});
```

Điều này giúp TanStack Query phân biệt chính xác cache của trang 1 và trang 2, hoặc khi người dùng thay đổi bộ lọc.

### 6.2 Tận Dụng Cache Invalidation Chiến Thuật
Thay vì refetch toàn bộ ứng dụng, chúng ta chỉ invalidate chính xác các query key bị ảnh hưởng.
* Khi thao tác trên một bản ghi cụ thể, ta có thể invalidate toàn bộ prefix key để làm mới danh sách:
  ```typescript
  // Mutation duyệt sản phẩm
  const queryClient = useQueryClient();
  useMutation({
    mutationFn: approveProduct,
    onSuccess: () => {
      // Invalidate toàn bộ các query bắt đầu bằng 'admin-pending-products'
      queryClient.invalidateQueries({ queryKey: ['admin-pending-products'] });
    }
  });
  ```

### 6.3 Xử Lý Trạng thái UI Hợp Lý (Loading, Error & Empty)
Tận dụng triệt để các biến trạng thái từ `useQuery` để render UI:
* `isLoading`: Hiển thị Skeleton Loader hoặc Spinner.
* `isError`: Hiển thị thông điệp báo lỗi trực quan hoặc nút "Tải lại" (`refetch()`).
* Check dữ liệu rỗng (`data.length === 0`): Render component `<EmptyState />` thay vì màn hình trắng trống trải.
