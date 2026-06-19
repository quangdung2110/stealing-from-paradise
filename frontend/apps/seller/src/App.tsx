import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import SidebarLayout from '@shared/components/SidebarLayout';
import PrivateRoute from '@shared/components/PrivateRoute';
import { PageLoader } from '@shared/components/ui';

const LoginPage              = lazy(() => import('@shared/pages/LoginPage'));
const SellerRegisterPage     = lazy(() => import('@/pages/SellerRegisterPage'));
const SellerDashboard        = lazy(() => import('@/pages/SellerDashboard'));
const ProductManagementPage   = lazy(() => import('@/pages/ProductManagementPage'));
const SellerFlashSalePage     = lazy(() => import('@/pages/SellerFlashSalePage'));
const SellerOrdersPage       = lazy(() => import('@/pages/SellerOrdersPage'));
const SellerOrderDetailPage  = lazy(() => import('@/pages/SellerOrderDetailPage'));
const StripeOnboardingPage   = lazy(() => import('@/pages/StripeOnboardingPage'));
const SellerPaymentsPage     = lazy(() => import('@/pages/SellerPaymentsPage'));
const SellerSettingsPage      = lazy(() => import('@/pages/SellerSettingsPage'));

const NAV_GROUPS = [
  {
    label: 'Tổng quan',
    items: [{ label: 'Dashboard', to: '/dashboard', iconKey: 'grid' as const }],
  },
  {
    label: 'Bán hàng',
    items: [
      { label: 'Sản phẩm', to: '/products', iconKey: 'cube' as const },
      { label: 'Flash Sale', to: '/flash-sales', iconKey: 'bolt' as const },
      { label: 'Đơn hàng', to: '/orders', iconKey: 'clipboard' as const },
    ],
  },
  {
    label: 'Tài chính',
    items: [
      { label: 'Thu nhập', to: '/payments', iconKey: 'money' as const },
      { label: 'Stripe', to: '/stripe-onboarding', iconKey: 'card' as const },
    ],
  },
  {
    label: 'Khác',
    items: [{ label: 'Cài đặt', to: '/settings', iconKey: 'cog' as const }],
  },
];

export default function App() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        {/* Auth pages — no layout */}
        <Route path="/login"    element={<LoginPage title="Cửa hàng" redirectTo="/dashboard" showRegisterLink={false} />} />
        <Route path="/register" element={<SellerRegisterPage />} />

        {/* Protected pages — wrapped in Layout */}
        <Route
          path="/*"
          element={
            <SidebarLayout appName="FlashSale Seller" navGroups={NAV_GROUPS}>
              <Routes>
                <Route path="/dashboard"         element={<PrivateRoute role="SELLER"><SellerDashboard /></PrivateRoute>} />
                <Route path="/products"          element={<PrivateRoute role="SELLER"><ProductManagementPage /></PrivateRoute>} />
                <Route path="/flash-sales"       element={<PrivateRoute role="SELLER"><SellerFlashSalePage /></PrivateRoute>} />
                <Route path="/orders"            element={<PrivateRoute role="SELLER"><SellerOrdersPage /></PrivateRoute>} />
                <Route path="/orders/:orderId"   element={<PrivateRoute role="SELLER"><SellerOrderDetailPage /></PrivateRoute>} />
                <Route path="/stripe-onboarding" element={<PrivateRoute role="SELLER"><StripeOnboardingPage /></PrivateRoute>} />
                {/* Stripe redirects sellers here after KYC; bounce back to onboarding page with hint flags */}
                <Route path="/stripe/return"  element={<Navigate to="/stripe-onboarding?from=stripe" replace />} />
                <Route path="/stripe/refresh" element={<Navigate to="/stripe-onboarding?refresh=1"  replace />} />
                <Route path="/payments"          element={<PrivateRoute role="SELLER"><SellerPaymentsPage /></PrivateRoute>} />
                <Route path="/settings"           element={<PrivateRoute role="SELLER"><SellerSettingsPage /></PrivateRoute>} />

                <Route path="/"  element={<Navigate to="/dashboard" replace />} />
                <Route path="*"  element={<Navigate to="/" replace />} />
              </Routes>
            </SidebarLayout>
          }
        />
      </Routes>
    </Suspense>
  );
}
