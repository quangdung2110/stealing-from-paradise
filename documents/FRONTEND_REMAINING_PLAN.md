# Frontend — Plan công việc còn lại

> Rà soát code thực tế ngày **2026-06-14**. Tài liệu này thay thế phần backlog của `FRONTEND_PLAN.md` (đã lỗi thời).
>
> **Bối cảnh:** Part A (A0–A7) + Part D (D1–D5) của `FRONTEND_PLAN.md` **gần như đã hoàn thành hết**. Trước khi làm bất kỳ mục nào dưới đây, **đọc code thật rồi hãy làm** — đừng tin tài liệu cũ.

## ✅ Đã hoàn thành trong đợt này
- **Hệ thống Toast (`sonner`)** — wrapper dùng chung `frontend/shared/lib/toast.tsx` (`notify.*` + `<AppToaster/>`), mount ở root cả 3 app; app không import `sonner` trực tiếp (lowest coupling). Thay 3 `alert()` chặn UI + sửa lỗi nuốt lỗi ở `ConfirmReceivedModal`.
- **Refund item: tên + ảnh sản phẩm (BE + FE)** — snapshot `product_name`/`image_snapshot` lúc tạo refund:
  - order-service: nhét vào event `REFUND_REQUESTED` (2 luồng partial).
  - refund-service: cột mới Flyway `V4`, field entity `RefundItem`, `PartialRefundHandler` lưu, `RefundDetailResponse`/`RefundQueryService` trả ra (admin REST), reply map thêm key camelCase.
  - FE admin (`RefundDetailDrawer`, `ApproveRefundModal`) render ảnh + tên thay vì `Item #id`.
  - **Trạng thái:** compile sạch (BE) + tsc sạch (FE), **chưa verify runtime** (cần rebuild — xem P0).

---

## 🔴 P0 — Kích hoạt & kiểm chứng (ĐÃ HOÀN THÀNH)
1. **[x] Rebuild BE** để Flyway `V4` chạy + event mới chảy:
   ```bash
   docker compose -f docker-compose-backend.yml up -d --build refund-service order-service
   ```
   **Verify:** tạo 1 yêu cầu **hoàn tiền một phần** mới → kiểm tra admin drawer + buyer history + seller panel đều thấy **ảnh + tên** sản phẩm. (Refund cũ trước migration: `product_name` null ở admin; buyer/seller vẫn lấy được qua join enrich.)
2. **[x] Smoke test toast** cả 3 app: xóa danh mục lỗi → toast đỏ; xác nhận đã nhận hàng → toast xanh.

## 🟠 P1 — Trang còn thiếu polish (ĐÃ HOÀN THÀNH)
- **[x] B3 — Checkout stepper** — thêm thanh bước `Giỏ hàng → Xem lại → Thanh toán → Hoàn tất` cho `OrderReviewPage.tsx` / `CheckoutPage.tsx` / `CheckoutResultPage.tsx`.
- **[x] B2 — Product detail** — gallery + breadcrumb đã có; chỉ bổ sung khu "thêm vào giỏ" **sticky trên mobile** (`ProductDetailPage.tsx`).
- **[x] B5 — Admin tables** — sticky header + filter chips + pagination đẹp + `<EmptyState>`/`<Skeleton>` đồng bộ cho `UserManagement`, `ProductModeration`, `Refunds`. Rà từng trang (search đã nối ở UserManagement).
- **[x] B7 — Grid/spacing** — thêm breakpoint tablet cho stats grid (`AdminDashboard.tsx`, `SellerDashboard.tsx`) + `Footer.tsx`.

## 🟡 P2 — Micro-interactions & dọn dẹp (ĐÃ HOÀN THÀNH)
- **[x] C2 — Skeleton sweep** — còn ~32 file dùng "Đang tải…"/spinner-text (vd `SellerStripePage` dùng ⏳) → thay bằng `<Skeleton>` ở các trang chính.
- **[x] C5 — Optimistic cart** — `shared/store/cartStore.ts` add/remove/update cập nhật lạc quan trước khi server trả về.
- **[x] C4 — ChatWidget** — minimize/resize + nhớ trạng thái.
- **[x] Dọn dẹp**:
  - Thay native `confirm()` (`ProductFormModal.tsx:195`) bằng `ConfirmDialog`.
  - Mở rộng `notify` cho các chỗ còn nuốt lỗi bằng `console.error` (lỗi cart, fetch suggestions…).

## 🟢 P3 — Cần thêm dependency (PHẢI DUYỆT trước khi làm)
- **[ ] B6 — Seller dashboard charts** → `recharts` *(chưa duyệt)* — thay số liệu text bằng biểu đồ doanh thu/đơn hàng.
- **[ ] C3 — Page transitions** → `framer-motion` hoặc CSS thuần *(chưa duyệt)*.
- **[ ] C6 — Dark mode** → CSS variables + toggle (lớn, làm cuối cùng).

## 🔧 Nợ kỹ thuật / audit (ĐÃ HOÀN THÀNH)
- **[x] Audit snake/camel cho Kafka request/reply trong order-service.** ObjectMapper order-service dùng chiến lược **mặc định (camelCase)**, không có `SNAKE_CASE` ở đâu trong backend; nhiều reply lại phát **snake_case** → field bị `null` thầm lặng. Đợt này đã vá đường **refund reply** (thêm key camelCase). **Cùng pattern có thể ảnh hưởng các reply DTO khác** → nên rà tất cả consumer reply trong order-service.
- **[x] D4 `InventoryPanel`** còn dòng *"Tính năng nhật ký tồn kho đang được phát triển"* (`InventoryPanel.tsx:191`) → xác nhận backend trả logs thật hay gỡ placeholder.
- **[x] Cập nhật `FRONTEND_PLAN.md`** đánh dấu A/D đã done để khỏi gây nhầm cho phiên/dev sau.

---

## Thứ tự đề xuất
**P0 (kiểm chứng)** → **B3 stepper** → **C2 / C5** (cảm giác mượt) → **B5 / B7** → **P3** (nếu duyệt dependency).

## Quy ước khi làm (giữ nguyên từ FRONTEND_PLAN.md)
- Dùng primitive `@shared/components/ui` + `<Icon>`; dùng `notify` cho feedback; **không** thêm icon library.
- Surgical changes — chỉ sửa đúng phạm vi; text/comment tiếng Việt.
- `cd frontend/apps/<app> && npx tsc --noEmit` sạch cho mọi app bị ảnh hưởng trước khi báo xong.
- Mục cần thư viện (chart/animation) → **HỎI user trước**.

