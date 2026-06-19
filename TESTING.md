# Hướng Dẫn Kiểm Thử Hệ Thống FlashSale Platform

Tài liệu này cung cấp hướng dẫn chi tiết về cách quản lý dữ liệu mẫu (dataset) và thực hiện kiểm thử tự động (Unit, Integration, E2E) cũng như kiểm thử thủ công (API) trên hệ thống microservices.

---

## 1. Dữ Liệu Phát Triển & Kiểm Thử Mẫu (Dev Data Seeder)

Hệ thống tích hợp sẵn cơ chế tự động nạp dữ liệu mẫu (seed data) khi khởi chạy các microservices ở môi trường phát triển (`dev` profile). Dữ liệu này giúp lập trình viên và kiểm thử viên có ngay một hệ thống đầy đủ tài khoản, sản phẩm, đơn hàng, giao dịch, và flash sale để thực hiện test nhanh.

### Cách Kích Hoạt & Reset Dữ Liệu
Cơ chế seeding được quản lý riêng trong từng service thông qua `CommandLineRunner` và được kiểm soát bởi file `application-dev.yml` (hoặc biến môi trường).

Cấu hình mặc định trong `application-dev.yml` của các service:
```yaml
dev-data:
  enabled: true    # Kích hoạt bộ nạp dữ liệu dev
  reset: false     # Nếu true, sẽ xóa sạch DB hiện tại của service đó trước khi seed lại
```

* **Để xóa sạch và nạp lại dữ liệu (Wipe + Reseed):**
  Cách nhanh nhất là sử dụng target trong `Makefile` hoặc truyền biến môi trường `DEV_DATA_RESET=true` khi khởi động service qua Docker / Maven:
  ```bash
  # Reset dữ liệu của riêng product-service
  make seed-reset S=product-service

  # Hoặc restart toàn bộ stack với cờ reset
  DEV_DATA_RESET=true docker compose up -d
  ```

---

## 2. Danh Mục Dataset Đã Được Seed Sẵn

Để giúp việc kiểm thử dễ dàng hơn, các bộ dữ liệu mẫu đã được catalog hóa dưới dạng JSON trong thư mục `test-datasets/` ở gốc dự án:

1. **[users.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/users.json):** Chứa thông tin 13 tài khoản người dùng được tạo sẵn.
   * **Mật khẩu chung cho tất cả tài khoản:** `dev123`
   * **Sellers (ID: 1 - 5):** `techworld`, `fashionhub`, `gadgetpro`, `homeliving`, `sportoutdoor`. (Đã được liên kết tài khoản Stripe Connect Connect ID tương ứng dạng `acct_test_*`).
   * **Buyers (ID: 6 - 9, 11 - 13):** `minhhoa`, `phuongthao`, `ductran`, `linhnguyen`, `huyenvu`, `tuananh`, `thanhhuyen`.
   * **Platform Admin (ID: 10):** `admin`.

2. **[products.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/products.json):** Danh mục 18 sản phẩm thuộc các ngành hàng Electronics, Audio, Accessories, Laptops, Wearables, Kitchen, Sports, Home & Living.
   * Chứa đầy đủ SKU code (ví dụ: `SKU-IPHONE-BLK-128`, `SKU-MACBOOK-AIR-M3`) khớp với cấu hình trong order-service.
   * Chứa thông tin tồn kho (stock) và trạng thái duyệt sản phẩm (`ACTIVE`, `PENDING`, `REJECTED`).

3. **[orders.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/orders.json):** Chứa 10 đơn hàng mẫu với đầy đủ các trạng thái từ `PENDING` (chờ thanh toán), `CONFIRMED` (đã thanh toán), `SHIPPED` (đang giao), `DELIVERED` (đã giao) đến `CANCELLED` (đã hủy).

4. **[payments.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/payments.json):** Chứa các giao dịch Stripe tương ứng với các đơn hàng mẫu, bao gồm cả các bản ghi phân bổ hoa hồng nền tảng (`seller_transfers`) và các yêu cầu hoàn tiền (`refunds`).

5. **[flashsales.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/flashsales.json):** Gồm 3 phiên Flash Sale mẫu:
   * **Phiên 1 - ENDED:** Đã kết thúc hôm qua.
   * **Phiên 2 - LIVE:** Đang diễn ra (kéo dài thêm 2 giờ). Chứa các sản phẩm giảm giá cực sâu như MacBook Air M3, Loa JBL Flip 6.
   * **Phiên 3 - UPCOMING:** Sẽ diễn ra vào ngày mai. Khách hàng `minhhoa`, `phuongthao`, `ductran` đã đăng ký nhận thông báo (`reminders`).

---

## 3. Quy Trình Kiểm Thử Tự Động (Automated Testing)

Hệ thống hỗ trợ cả unit tests và E2E tests tích hợp sâu:

### A. Kiểm Thử Backend (JUnit / Java E2E)
1. **Chạy Unit/Integration Tests toàn bộ hệ thống:**
   Kiểm tra tính đúng đắn của code logic trong từng microservice.
   ```bash
   cd backend
   mvn clean test
   ```

2. **Chạy Java E2E Tests (Black-box Integration qua Gateway):**
   Chạy các bài kiểm thử đầu cuối giao tiếp trực tiếp với cổng API Gateway (`http://localhost:8080`) để giả lập hành vi thực tế của client.
   ```bash
   cd backend
   mvn -pl e2e-tests test -Pe2e
   ```

### B. Kiểm Thử E2E Qua Sidecar Container (Python E2E)
Do Docker Desktop trên Windows đôi khi gặp lỗi Port-Proxy khi map cổng localhost, dự án cung cấp bộ script Python chạy trực tiếp bên trong Docker Network thông qua sidecar container `e2e-runner`.

* **Chạy toàn bộ kịch bản kiểm thử E2E (Phổ biến nhất):**
   ```bash
   bash backend/e2e-tests/scripts/run_e2e_tests.sh
   ```
* **Chạy một nhóm cụ thể (ví dụ: auth, general, stripe):**
   ```bash
   bash backend/e2e-tests/scripts/run_e2e_tests.sh --group auth
   ```
* **Chạy một ca kiểm thử đơn lẻ:**
   ```bash
   bash backend/e2e-tests/scripts/run_e2e_tests.sh --test t_auth_login_buyer
   ```
* **Liệt kê danh sách tất cả ca kiểm thử khả dụng:**
   ```bash
   bash backend/e2e-tests/scripts/run_e2e_tests.sh --list
   ```

### C. Kiểm Thử Frontend (Vitest)
Cả hai ứng dụng customer-app, seller-app và thư viện dùng chung đều được viết test bằng Vitest.
```bash
# Test ứng dụng Customer
cd frontend/apps/customer
npm test

# Test ứng dụng Seller
cd frontend/apps/seller
npm test

# Test thư viện Shared
cd frontend/shared
npm test
```

---

## 4. Hướng Dẫn Kiểm Thử Thủ Công Qua API (Manual API Scenarios)

Bạn có thể sử dụng Postman, Insomnia hoặc `cURL` để kiểm thử các luồng nghiệp vụ lõi bằng cách sử dụng các file JSON mẫu nằm tại thư mục `test-datasets/api-requests/`.

### Kịch Bản 1: Luồng Mua Hàng & Thanh Toán Lõi (Checkout & Payment Flow)
1. **Đăng nhập:**
   Gửi yêu cầu POST tới `/api/v1/auth/login` bằng nội dung của [login-buyer.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/login-buyer.json). Nhận về `accessToken`.
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d @test-datasets/api-requests/login-buyer.json
   ```
2. **Thêm sản phẩm vào giỏ hàng:**
   Sử dụng token lấy được ở bước 1 gửi trong Header `Authorization: Bearer <token>`. Thêm sản phẩm qua POST `/api/v1/cart/items` bằng [add-to-cart.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/add-to-cart.json). (Thay thế `VARIANT_UUID_OR_SKU_GOES_HERE` bằng một SKU hợp lệ như `SKU-IPHONE-BLK-128`).
3. **Xem trước đơn hàng (Checkout Preview):**
   Gửi POST `/api/v1/cart/checkout/preview` bằng [checkout-preview.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/checkout-preview.json) (thay `VARIANT_ID` bằng ID variant từ giỏ hàng). Endpoint sẽ trả về `previewToken`.
4. **Xác nhận đặt hàng (Submit Order):**
   Gửi POST `/api/v1/cart/checkout/submit` bằng [checkout-submit.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/checkout-submit.json). Nhận về `parentOrderId`. Lúc này, đơn hàng có trạng thái `PENDING` chờ thanh toán.
5. **Giả lập Webhook thanh toán Stripe thành công:**
   Do chạy dev local không có cổng thanh toán thật, ta giả lập Webhook từ Stripe gửi tới API của payment-service:
   POST `/api/v1/stripe/webhooks` bằng [stripe-webhook-succeeded.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/stripe-webhook-succeeded.json) (chú ý thay đổi `parent_order_id` trong phần metadata khớp với ID đơn vừa tạo ở bước 4).
   * *Lưu ý:* Cần ký header `Stripe-Signature` tương ứng để pass filter bảo mật của webhook (hoặc sử dụng file `backend/e2e-tests/scripts/forge.py` để tự động ký).

### Kịch Bản 2: Đăng Ký & Mua Flash Sale (Flash Sale Flow)
1. **Tạo phiên Flash Sale (Admin):**
   Đăng nhập tài khoản `admin` bằng [login-admin.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/login-admin.json). Tạo phiên mới qua POST `/api/v1/flash-sales` bằng [create-flashsale-session.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/create-flashsale-session.json). Nhận về `sessionId` (`sid`).
2. **Kích hoạt phiên:**
   Gửi PUT `/api/v1/flash-sales/{sid}` với body `{"status": "ACTIVE"}`.
3. **Đăng ký sản phẩm tham gia Flash Sale (Seller):**
   Đăng nhập tài khoản seller `techworld` bằng [login-seller.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/login-seller.json).
   Đăng ký sản phẩm qua POST `/api/v1/flash-sales/{sid}/items` bằng [register-flashsale-item.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/register-flashsale-item.json).
4. **Duyệt sản phẩm tham gia (Admin):**
   Sử dụng token Admin gửi POST `/api/v1/flash-sales/{sid}/items/{fi}/approve` (với `fi` là ID item flash sale vừa tạo).
5. **Mua hàng Flash Sale (Buyer):**
   Đăng nhập buyer `minhhoa`. Gửi POST `/api/v1/flash-sales/{sid}/buy` bằng [buy-flashsale-item.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/buy-flashsale-item.json). Giao dịch sẽ được xử lý bất đồng bộ qua Redis Lua script để chịu tải cao.

### Kịch Bản 3: Yêu Cầu Hoàn Tiền (Refund Flow)
1. **Tạo yêu cầu hoàn tiền:**
   Đăng nhập buyer `minhhoa`. Đối với một đơn hàng đã giao (`DELIVERED`), gửi yêu cầu hoàn tiền qua POST `/api/v1/orders/{orderId}/refunds` bằng [create-refund.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/create-refund.json).
2. **Duyệt hoàn tiền (Admin):**
   Đăng nhập tài khoản Admin, lấy danh sách refund chờ duyệt tại `/api/v1/admin/refunds?page=0&size=10`.
   Duyệt yêu cầu qua POST `/api/v1/admin/refunds/{refundId}/approve` với body `{"adminNote": "Đã xác nhận lỗi hỏng"}`. Hệ thống sẽ tự động hoàn tiền qua cổng Stripe Connect và gửi Kafka event cập nhật trạng thái đơn hàng về `REFUNDED`.

### Kịch Bản 4: Trò Chuyện Với Trợ Lý AI (AI Chat flow)
1. **Khởi tạo phiên chat:**
   Đăng nhập buyer `minhhoa` (cần gửi Header `X-User-Id: 6` và Authorization token).
   Gửi POST `/api/ai/sessions` để tạo phiên chat mới. Nhận về `sessionId` (`sid`).
2. **Gửi tin nhắn hỏi mua hàng / đặt hàng:**
   Gửi POST `/api/ai/chat` (Server Sent Events) bằng [ai-chat.json](file:///D:/dev/cc113/stealing-from-paradise/test-datasets/api-requests/ai-chat.json). Trợ lý AI sẽ stream câu trả lời và tự động gọi tool để tìm kiếm sản phẩm hoặc tạo nháp giỏ hàng phù hợp.

---

## 5. Stripe Integration Test Mode

Hệ thống được thiết kế để kiểm thử tích hợp Stripe Connect một cách trọn vẹn:
* **Stripe Webhook Listener:** Để nhận webhook thật từ Stripe ở local, chạy tool listener để forward sự kiện:
  ```bash
  # Khởi chạy stripe CLI listener trong dev profile
  make stripe
  ```
* **Stripe Hosted Onboarding:** Lập trình viên truy cập trang quản lý của seller tại `http://localhost:3001` → Cài đặt thanh toán → Connect Stripe để thực hiện onboarding tài khoản Stripe test. Trạng thái onboarding thành công sẽ tự động cập nhật trong cơ sở dữ liệu và cho phép shop bắt đầu nhận thanh toán.
* **Test Card:** Khi thanh toán qua cổng Customer App (`http://localhost:3000`), sử dụng thẻ test Stripe:
  * Số thẻ: `4242 4242 4242 4242`
  * Ngày hết hạn: Bất kỳ ngày nào trong tương lai (ví dụ: `12/34`)
  * CVC: `123`
