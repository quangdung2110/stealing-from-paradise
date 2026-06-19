import { lazy, Suspense, useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from '@shared/components/Layout';
import PrivateRoute from '@shared/components/PrivateRoute';
import { PageLoader } from '@shared/components/ui';
import ChatWidget from '@/components/ChatWidget';
import { useAuthStore } from '@shared/store/authStore';
import { useWishlistStore } from '@shared/store/wishlistStore';

const LoginPage          = lazy(() => import('@shared/pages/LoginPage'));
const RegisterPage       = lazy(() => import('@shared/pages/RegisterPage'));
const ProductListPage    = lazy(() => import('@/pages/ProductListPage'));
const ProductDetailPage  = lazy(() => import('@/pages/ProductDetailPage'));
const CartPage           = lazy(() => import('@/pages/CartPage'));
const WishlistPage       = lazy(() => import('@/pages/WishlistPage'));
const OrderReviewPage    = lazy(() => import('@/pages/OrderReviewPage'));
const CheckoutResultPage = lazy(() => import('@/pages/CheckoutResultPage'));
const FlashSalePage      = lazy(() => import('@/pages/FlashSalePage'));
const OrderHistoryPage   = lazy(() => import('@/pages/OrderHistoryPage'));
const OrderDetailPage    = lazy(() => import('@/pages/OrderDetailPage'));
const ProfilePage        = lazy(() => import('@/pages/ProfilePage'));
const AddressPage        = lazy(() => import('@/pages/AddressPage'));
const AccountSettingsPage = lazy(() => import('@/pages/AccountSettingsPage'));
const RefundHistoryPage = lazy(() => import('@/pages/RefundHistoryPage'));
const NotificationsPage = lazy(() => import('@/pages/NotificationsPage'));
const SellerStorePage   = lazy(() => import('@/pages/SellerStorePage'));

// Top-priority destinations shown inline on the desktop bar. The cart lives in
// its own badge icon, so it's not repeated here.
const PRIMARY_ITEMS = [
  { label: 'Sản phẩm', to: '/products', iconKey: 'bag' as const },
  { label: 'Flash Sale', to: '/flash-sales', iconKey: 'bolt' as const },
  { label: 'Đơn hàng', to: '/orders', iconKey: 'receipt' as const },
];

// Everything else is grouped inside the account dropdown instead of crowding the bar.
const MENU_GROUPS = [
  {
    label: 'Hoạt động',
    items: [
      { label: 'Yêu thích', to: '/wishlist', iconKey: 'heart' as const },
      { label: 'Hoàn tiền', to: '/refunds', iconKey: 'refund' as const },
      { label: 'Thông báo', to: '/notifications', iconKey: 'bell' as const },
    ],
  },
  {
    label: 'Tài khoản',
    items: [
      { label: 'Hồ sơ', to: '/profile', iconKey: 'user' as const },
      { label: 'Địa chỉ', to: '/addresses', iconKey: 'mapPin' as const },
      { label: 'Cài đặt', to: '/account-settings', iconKey: 'cog' as const },
    ],
  },
];

// Mobile bottom tab bar — four most-used destinations, always one tap away.
const BOTTOM_TABS = [
  { label: 'Sản phẩm', to: '/products', iconKey: 'bag' as const },
  { label: 'Flash Sale', to: '/flash-sales', iconKey: 'bolt' as const },
  { label: 'Giỏ hàng', to: '/cart', iconKey: 'cart' as const, badge: 'cart' as const },
  { label: 'Đơn hàng', to: '/orders', iconKey: 'receipt' as const },
];

export default function App() {
  const { isAuthenticated } = useAuthStore();
  const fetchWishlist = useWishlistStore((s) => s.fetchWishlist);
  const resetWishlist = useWishlistStore((s) => s.reset);

  // Nạp danh sách yêu thích một lần khi đăng nhập (tô màu nút tim trên card);
  // đăng xuất thì xóa để không lộ tim của user trước.
  useEffect(() => {
    if (isAuthenticated) fetchWishlist();
    else resetWishlist();
  }, [isAuthenticated, fetchWishlist, resetWishlist]);

  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        {/* Auth pages — no layout */}
        <Route path="/login"    element={<LoginPage redirectTo="/products" />} />
        <Route path="/register" element={<RegisterPage redirectTo="/products" />} />

        {/* All other pages — wrapped in Layout */}
        <Route
          path="/*"
          element={
            <Layout
              appName="FlashSale"
              primaryItems={PRIMARY_ITEMS}
              menuGroups={MENU_GROUPS}
              bottomTabItems={BOTTOM_TABS}
              showSearch
              showCart
            >
              <Routes>
                <Route path="/products"        element={<ProductListPage />} />
                <Route path="/product"         element={<ProductListPage />} />
                <Route path="/products/:productId" element={<ProductDetailPage />} />
                <Route path="/product/:productId"  element={<ProductDetailPage />} />
                <Route path="/flash-sales"     element={<FlashSalePage />} />
                <Route path="/sellers/:sellerId" element={<SellerStorePage />} />

                <Route path="/cart"     element={<PrivateRoute><CartPage /></PrivateRoute>} />
                <Route path="/wishlist" element={<PrivateRoute><WishlistPage /></PrivateRoute>} />
                <Route path="/checkout" element={<PrivateRoute><OrderReviewPage /></PrivateRoute>} />
                <Route path="/checkout/result" element={<CheckoutResultPage />} />
                <Route path="/orders"   element={<PrivateRoute><OrderHistoryPage /></PrivateRoute>} />
                <Route path="/orders/:parentOrderId" element={<PrivateRoute><OrderDetailPage /></PrivateRoute>} />
                <Route path="/refunds" element={<PrivateRoute><RefundHistoryPage /></PrivateRoute>} />

                <Route path="/profile"          element={<PrivateRoute><ProfilePage /></PrivateRoute>} />
                <Route path="/addresses"        element={<PrivateRoute><AddressPage /></PrivateRoute>} />
                <Route path="/account-settings" element={<PrivateRoute><AccountSettingsPage /></PrivateRoute>} />
                <Route path="/notifications"    element={<PrivateRoute><NotificationsPage /></PrivateRoute>} />

                <Route path="/"  element={<Navigate to="/products" replace />} />
                <Route path="*"  element={<Navigate to="/" replace />} />
              </Routes>
            </Layout>
          }
        />
      </Routes>
      {isAuthenticated && <ChatWidget />}
    </Suspense>
  );
}
