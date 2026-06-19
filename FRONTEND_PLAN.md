# Frontend Improvement Plan — Handoff Document

> Tài liệu này để bàn giao cho một AI/dev khác thực thi. Đọc hết **Phần 0 (Bối cảnh)** và **Phần 1 (Đã xong)** trước khi code, để không làm lại hoặc phá vỡ quy ước đã thiết lập.

---

## 0. Bối cảnh dự án

**Monorepo:** `frontend/` chứa 3 app React + TypeScript:
- `apps/customer` — app người mua
- `apps/admin` — trang quản trị
- `apps/seller` — trang người bán

**Stack:** React 19, Vite 6, React Router 6, Zustand (store), TanStack Query, Tailwind CSS 3, axios. Code dùng path alias `@shared/*` → `frontend/shared/*` và `@/*` → `src/*` của từng app.

**Layer dùng chung:** `frontend/shared/` chứa `api/` (15 service), `store/` (zustand), `components/` (UI dùng chung), `pages/` (Login/Register).

**Backend:** microservices Java/Spring tại `backend/` (order, payment, product, refund, identity, notification, chat, search, flashsale services). Frontend gọi qua API gateway.

### Lệnh quan trọng
```bash
# Typecheck 1 app (KHÔNG cần rollup — chạy được trên Windows):
cd frontend/apps/<app> && npx tsc --noEmit

# Dev server:
cd frontend/apps/<app> && npm run dev

# Test:
cd frontend/apps/<app> && npm run test   # vitest
```

### ⚠️ Lưu ý môi trường
- **Windows:** `npm install` cần `--force` (lỗi `EBADPLATFORM` của rollup-linux trên Windows). `vite build`/`vite preview` có thể vướng lỗi rollup native — **dùng `tsc --noEmit` để verify thay vì build** nếu gặp lỗi này.
- Tailwind `plugins: []` ở cả 3 config → các class `animate-in / fade-in / zoom-in-* / slide-in-*` là **no-op** (không lỗi, chỉ mất animation). Nếu cần animation thật: thêm `tailwindcss-animate`.

### Quy ước BẮT BUỘC tuân theo
1. **Surgical changes** — chỉ sửa đúng cái được yêu cầu, không refactor code lân cận, match style hiện có. Mỗi dòng đổi phải truy về được yêu cầu.
2. **Dùng design system có sẵn** (xem Phần 1) thay vì tự viết markup mới.
3. **Icons:** dùng `<Icon name="..." />` từ `frontend/shared/components/icons.tsx`. **Không** thêm icon library. Thiếu icon thì thêm path vào `icons.tsx`.
4. **Verify:** chạy `tsc --noEmit` cho mọi app bị ảnh hưởng trước khi báo xong.
5. Comment & text UI viết tiếng Việt (match codebase).

---

## 1. ĐÃ HOÀN THÀNH (đừng làm lại — hãy TẬN DỤNG)

### Phase 1 — Navigation restructure ✅
- **Customer:** `shared/components/Layout.tsx` = top `Navbar` (3 link chính + search box + cart-badge icon + NotificationBell + dropdown tài khoản) + `Footer` + `BottomTabBar` (mobile). Link phụ nằm trong `menuGroups` của dropdown, KHÔNG trên thanh bar.
- **Admin & Seller:** `shared/components/SidebarLayout.tsx` = sidebar tối thu gọn được (grouped `navGroups`) + top bar + drawer mobile. **Không** dùng `Layout` nữa.
- Config nav truyền từ mỗi `apps/*/src/App.tsx` qua type `NavItem`/`NavGroup` (`shared/components/navConfig.ts`).
- Header search customer điều hướng `/products?q=...`; `ProductListPage` đọc param qua `useSearchParams`.

### Phase 2 — Design system ✅
**Primitives** tại `frontend/shared/components/ui/`, import qua barrel `@shared/components/ui`:

| Component | API chính |
|-----------|-----------|
| `Button` | `variant` (primary/secondary/outline/ghost/danger), `size` (sm/md/lg), `loading`, `fullWidth` |
| `Card` | `hoverable`, `padded` |
| `Badge` | `tone` (neutral/brand/success/warning/danger/info), `dot` |
| `Modal` | `open`, `onClose`, `title`, `footer`, `size` (sm/md/lg), Escape-close |
| `EmptyState` | `iconKey`, `title`, `description`, `action` |
| `Spinner`, `Skeleton` | loading states |
| `PageHeader` | `title`, `subtitle`, `actions` |
| `Container` | wrapper `max-w-7xl mx-auto px-...` |
| `PageLoader` | fallback Suspense |
| `cn(...)` | class-joiner (thay clsx) |

**Design tokens** (trong cả 3 `apps/*/tailwind.config.js`):
- `colors.brand` — ramp 50→950 (xanh). *Code cũ vẫn dùng `blue-*`; migrate dần khi đụng tới.*
- `boxShadow.card`, `boxShadow.card-hover` — độ nổi chuẩn của card (dùng bởi `<Card>`).

> **Khi làm Phase 3/4 và các trang dưới đây: ưu tiên dùng các primitive này.** Ví dụ status đơn hàng → `<Badge tone="success">`, nút → `<Button>`, modal → `<Modal>`, danh sách rỗng → `<EmptyState>`, loading → `<Skeleton>`/`<Spinner>`.

---

## PHẦN A — BUG & CHỨC NĂNG CÒN THIẾU (ưu tiên cao nhất)

### A0. 🔴 [P0] Bug: ChatAI treo khung chat sau khi xác nhận hành động
**File:** `frontend/shared/store/chatStore.ts`
**Root cause:** `confirmAction` (≈ dòng 200–214) và `rejectAction` (≈ 216–230) set `isStreaming: true` ở đầu, gọi `fetchHistory`, nhưng **không bao giờ set lại `isStreaming: false` khi thành công**. Hậu quả: `isStreaming` kẹt `true` vĩnh viễn → ChatWidget thay ô input bằng nút "Dừng AI trả lời", nút này gọi `cancelStreaming()` nhưng `abortController` đã `null` → khung chat đơ.
**Fix:** sau `await fetchHistory(sessId)` (và trong nhánh không có sessId) phải `set({ isStreaming: false })`. Tốt nhất bọc `try/finally` đặt `set({ isStreaming: false })` trong `finally` cho cả 2 hàm.
**Acceptance:** Chat → yêu cầu hủy đơn → bấm "Đồng ý xác nhận" → sau khi xong, ô input quay lại bình thường, gõ tiếp được. Lặp lại với "Từ chối".

### A1. 🔴 [P0] Ảnh sản phẩm bị mất ở Cart / Checkout — root cause chung
**File:** `frontend/shared/api/cart.api.ts`
**Root cause:** `RawCartItemResponse` (≈ dòng 98–113) CÓ field `variantImageSnapshot?: string | null` (≈ dòng 106), nhưng `mapCartItem()` (≈ 124–139) **bỏ rơi** nó. `CartItem` interface (≈ 4–22) cũng **chưa có** field ảnh.
**Fix:**
1. Thêm `image?: string` vào interface `CartItem`.
2. Trong `mapCartItem`, map `image: raw.variantImageSnapshot ?? undefined`.
**Acceptance:** Sau fix, `cart.sellers[].items[].image` có URL ảnh. (Mở khoá cho A2.)

### A2. 🔴 [P0] Cart page hiển thị emoji thay vì ảnh
**File:** `frontend/apps/customer/src/pages/CartPage.tsx` (≈ dòng 173 — ô `🛍️` hardcode `w-20 h-20`).
**Fix:** thay emoji bằng `<img src={item.image} .../>` (fallback emoji/placeholder nếu `image` rỗng). Phụ thuộc A1.
**Acceptance:** giỏ hàng hiển thị ảnh thật của từng sản phẩm.

### A3. 🔴 [P0] Checkout review hiển thị emoji thay vì ảnh
**File:** `frontend/apps/customer/src/pages/OrderReviewPage.tsx` (≈ dòng 850 — ô `🛍️` `w-12 h-12`).
**Root cause:** `CheckoutPreviewItem` (trong `cart.api.ts` ≈ dòng 64–75) **đã có** `imageUrl?: string` nhưng trang đang bỏ qua.
**Fix:** render `item.imageUrl` (fallback nếu rỗng).
**Acceptance:** trang review checkout hiển thị ảnh sản phẩm.

### A4. 🟠 [P1] Order history không có thumbnail
**File:** `frontend/apps/customer/src/pages/OrderHistoryPage.tsx`
**Root cause:** type `OrderSummary` chỉ có orderId, orderCode, sellerName, status, totalAmt, finalAmt, itemCount, createdAt — **không có ảnh**.
**Cần kiểm tra backend trước:** xem order-service trả về list order có kèm ảnh/first-item-image không (`backend/order-service/...`). 2 hướng:
- (a) Nếu backend có field ảnh → thêm vào `OrderSummary` (trong `frontend/shared/api/order.api.ts`) và render thumbnail.
- (b) Nếu không → hiển thị icon/`<Badge>` trạng thái cho gọn; **không** fetch detail từng đơn (tránh N+1). Ghi rõ quyết định.
**Acceptance:** danh sách đơn trông gọn gàng, có thumbnail nếu backend hỗ trợ, nếu không thì có trạng thái rõ ràng bằng `<Badge>`.

---

### Refund Evidence — luồng E2E (upload → admin xem → buyer xem lại)

> Type đã sẵn sàng, chỉ thiếu UI. `frontend/shared/api/refund.api.ts`:
> - `FullRefundRequest.evidenceImages?: string[]` (≈ dòng 14)
> - `PartialRefundRequest.evidenceImages?: string[]` (≈ dòng 20)
> - `getRefundPresignedUrl()` (≈ dòng 167) — **chưa UI nào gọi**
> - `adminRefundApi.getById()` (≈ dòng 123) — **chưa UI nào gọi**
> Backend `backend/refund-service/.../dto/response/RefundDetailResponse.java` trả: `evidenceImages`, `returnEvidence` (type/trackingNumber/recordedAt), `items` (itemId/quantity/refundAmount/itemReason/status).

### A5. 🔴 [P0] Buyer: upload ảnh bằng chứng khi tạo refund
**File:** `frontend/apps/customer/src/pages/OrderDetailPage.tsx` — `FullRefundModal` (≈ 279–351) và `PartialRefundModal` (≈ 113–276).
**Hiện trạng:** 2 modal gửi `evidenceImages` trong request type nhưng **không có UI upload**.
**Fix:** thêm khu upload ảnh: chọn file → gọi `getRefundPresignedUrl()` → PUT ảnh lên S3/MinIO presigned URL → đưa URL public vào `evidenceImages[]` gửi kèm request. (Tham khảo pattern upload ảnh đã có trong product/seller nếu có.) Hiển thị preview thumbnail + cho xoá trước khi submit. Dùng `<Button>`, `<Spinner>` khi đang upload.
**Acceptance:** buyer đính kèm được nhiều ảnh khi yêu cầu hoàn tiền; ảnh lên S3; URL nằm trong payload.

### A6. 🟠 [P1] Admin: drawer refund hiển thị đầy đủ (ảnh + items)
**File:** `frontend/apps/admin/src/components/Refunds/RefundDetailDrawer.tsx` và `ApproveRefundModal.tsx`.
**Root cause:** Drawer nhận `RefundResponse` từ list (data cũ/thiếu), **không** fetch detail → thiếu `evidenceImages`, `returnEvidence`, `items`, `causedBy`.
**Fix:** khi mở drawer, gọi `adminRefundApi.getById(refundId)` lấy `RefundDetailResponse`; render gallery ảnh bằng chứng, danh sách items, return evidence. `ApproveRefundModal` cũng cho admin xem ảnh trước khi duyệt.
**Acceptance:** admin thấy ảnh bằng chứng buyer gửi + chi tiết item trước khi Approve/Reject.

### A7. 🟠 [P1] Buyer: lịch sử refund hiển thị ảnh + tên sản phẩm
**File:** `frontend/apps/customer/src/pages/RefundHistoryPage.tsx`.
**Hiện trạng:** không hiển thị ảnh bằng chứng; item chỉ ghi `x{quantity} sản phẩm` (thiếu tên/ảnh); không link sang chi tiết.
**Fix:** hiển thị ảnh bằng chứng (gallery), tên + ảnh sản phẩm; thêm link/expand xem chi tiết refund (dùng `adminRefundApi.getById` bản buyer nếu có, hoặc endpoint tương ứng).
**Acceptance:** buyer xem lại được ảnh đã gửi và thông tin sản phẩm trong mỗi refund.

---

## PHẦN B — UI/UX PHASE 3: Page-level polish (P1)

> Dùng primitive ở Phần 1. Mục tiêu: từng trang trông chuyên nghiệp, nhất quán.

### B1. Product cards redesign
**File:** `frontend/apps/customer/src/components/ProductCard.tsx` + `ProductListPage.tsx`.
- Ảnh tỉ lệ cố định (aspect-square), hover zoom nhẹ, overlay nút wishlist (tim), badge giảm giá/flash, rating sao (nếu có `rating`/`reviewsCount` trong `product.api.ts`).
- Grid breakpoint nhất quán: `grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5`, `gap` chuẩn.

### B2. Product detail upgrade
**File:** `frontend/apps/customer/src/pages/ProductDetailPage.tsx`.
- Image gallery (ảnh chính + thumbnail), breadcrumb, variant picker rõ ràng, khu "thêm vào giỏ" dính (sticky) trên mobile.

### B3. Checkout flow stepper
**File:** `OrderReviewPage.tsx`, `CheckoutPage.tsx`, `CheckoutResultPage.tsx`.
- Thanh stepper: Giỏ hàng → Xem lại → Thanh toán → Hoàn tất. Order summary gọn, dùng `<Card>`.

### B4. Order history & detail polish
**File:** `OrderHistoryPage.tsx`, `OrderDetailPage.tsx`.
- Status bằng `<Badge>` theo tone (pending=warning, paid=info, completed=success, cancelled=danger). Timeline trạng thái ở detail.

### B5. Admin tables chuyên nghiệp
**File:** `apps/admin/src/pages/*` (UserManagement, ProductModeration, Refunds...).
- Header sticky, filter chips, pagination đẹp, empty state bằng `<EmptyState>`, loading bằng `<Skeleton>`. Cân nhắc tách 1 component `DataTable` dùng chung nếu lặp nhiều (chỉ tách khi ≥3 chỗ dùng — tránh abstraction thừa).

### B6. Seller dashboard charts
**File:** `apps/seller/src/pages/SellerDashboard.tsx`.
- Thay số liệu text bằng chart thật (doanh thu, đơn hàng). **Cần thêm thư viện chart** (vd `recharts`) → HỎI USER trước khi thêm dependency.

### B7. Chuẩn hoá grid & spacing toàn cục
- Stats grid hiện `grid-cols-2 lg:grid-cols-4` (nhảy 2→4, bỏ qua tablet) ở `AdminDashboard.tsx:72`, `SellerDashboard.tsx:52` → thêm `md:grid-cols-3` hoặc `sm:grid-cols-2 lg:grid-cols-4` cho mượt.
- `Footer.tsx:9` grid `grid-cols-1 sm:grid-cols-3` → thêm breakpoint tablet.

---

## PHẦN C — UI/UX PHASE 4: Micro-interactions (P2)

- **C1. Toast notifications** — thêm hệ thống toast (vd `sonner`/`react-hot-toast`) thay `alert`/`console.error`. *Cần thêm dependency → hỏi user.*
- **C2. Skeleton loading** — thay mọi chỗ "Loading..." còn lại bằng `<Skeleton>`.
- **C3. Page transitions** — fade/slide giữa route (cần `framer-motion` → hỏi user; hoặc CSS thuần).
- **C4. ChatWidget polish** — cho resize/minimize, nhớ trạng thái.
- **C5. Optimistic UI** — cart add/remove cập nhật lạc quan trước khi server trả về.
- **C6. Dark mode** — CSS variables + toggle (lớn, làm cuối).

---

## PHẦN D — Chức năng phụ còn thiếu (P1–P2)

### D1. Seller xem trạng thái refund của đơn
**File:** `frontend/apps/seller/src/pages/SellerOrderDetailPage.tsx`. Hiện seller không thấy buyer đã yêu cầu refund chưa. Thêm khu hiển thị trạng thái refund (gọi refund API theo order).

### D2. Search page mở thêm filter backend đã hỗ trợ
**Backend** `backend/search-service/.../controller/SearchController.java` hỗ trợ `price_min`, `price_max`, `in_stock`, `is_flash`, `sort` — UI mới dùng `q` + `category_id`.
**File FE:** `ProductListPage.tsx` (đã có sẵn UI sort/price range cục bộ — nối vào API search là chính). Bổ sung filter `in_stock`, `is_flash`.

### D3. Notification click điều hướng tới entity liên quan
**File:** `frontend/apps/customer/src/pages/NotificationsPage.tsx` + `shared/components/NotificationBell.tsx`. Hiện click chỉ mark-as-read. Thêm điều hướng theo `type`/payload của notification (vd order → `/orders/:id`, refund → `/refunds`).

### D4. Seller inventory logs UI
**API có sẵn:** `frontend/shared/api/seller.api.ts` → `getInventoryLogs()` (chưa có UI). Tạo trang/khu xem lịch sử tồn kho.

### D5. Admin: trạng thái Stripe của seller
**API có sẵn:** `frontend/shared/api/admin.api.ts` → `getSellerStripeAccounts()` (≈ dòng 80) trả summary + danh sách account. Trang `SellerStripePage.tsx` đã tồn tại — kiểm tra đã render đầy đủ summary/status chưa, bổ sung nếu thiếu.

---

## Thứ tự đề xuất thực thi
1. **A0** (chat freeze — 2 dòng, nhanh, độc lập).
2. **A1 → A2 → A3** (ảnh cart/checkout — A1 mở khoá A2,A3).
3. **A5 → A6 → A7** (luồng refund evidence E2E).
4. **A4, D1–D5** (chức năng còn thiếu).
5. **Phase 3 (B1–B7)** rồi **Phase 4 (C1–C6)** (đánh bóng UI; các mục cần thêm dependency phải hỏi user).

## Checklist mỗi task
- [ ] Đọc file liên quan đầy đủ trước khi sửa (không đoán path/field).
- [ ] Dùng primitive `@shared/components/ui` + `<Icon>` thay vì tự viết.
- [ ] `cd frontend/apps/<app> && npx tsc --noEmit` sạch cho mọi app bị ảnh hưởng.
- [ ] Thay đổi tối thiểu, không refactor ngoài phạm vi.
- [ ] Mục cần thêm thư viện (chart/toast/animation) → HỎI user trước.
