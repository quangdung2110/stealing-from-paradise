import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import SidebarLayout from '@shared/components/SidebarLayout';
import PrivateRoute from '@shared/components/PrivateRoute';
import { PageLoader } from '@shared/components/ui';

const LoginPage             = lazy(() => import('@shared/pages/LoginPage'));
const AdminDashboard        = lazy(() => import('@/pages/AdminDashboard'));
const UserManagementPage    = lazy(() => import('@/pages/UserManagementPage'));
const CategoryManagementPage = lazy(() => import('@/pages/CategoryManagementPage'));
const ProductModerationPage = lazy(() => import('@/pages/ProductModerationPage'));
const RefundsPage           = lazy(() => import('@/pages/RefundsPage'));
const FlashSaleConfigPage   = lazy(() => import('@/pages/FlashSaleConfigPage'));
const SellerStripePage       = lazy(() => import('@/pages/SellerStripePage'));
const BannerManagementPage   = lazy(() => import('@/pages/BannerManagementPage'));

const NAV_GROUPS = [
  {
    label: 'Tổng quan',
    items: [{ label: 'Dashboard', to: '/dashboard', iconKey: 'grid' as const }],
  },
  {
    label: 'Quản lý',
    items: [
      { label: 'Danh mục', to: '/categories', iconKey: 'tag' as const },
      { label: 'Người dùng', to: '/users', iconKey: 'users' as const },
      { label: 'Duyệt sản phẩm', to: '/product-moderation', iconKey: 'checkBadge' as const },
      { label: 'Banner', to: '/banners', iconKey: 'grid' as const },
    ],
  },
  {
    label: 'Tài chính',
    items: [
      { label: 'Hoàn tiền', to: '/refunds', iconKey: 'refund' as const },
      { label: 'Flash Sale', to: '/flash-sale-config', iconKey: 'bolt' as const },
      { label: 'Sellers (Stripe)', to: '/sellers-stripe', iconKey: 'card' as const },
    ],
  },
];

export default function App() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        {/* Auth page — no layout, no register for admin */}
        <Route path="/login" element={<LoginPage title="Admin Login" redirectTo="/dashboard" showRegisterLink={false} />} />

        {/* Protected pages — wrapped in Layout */}
        <Route
          path="/*"
          element={
            <SidebarLayout appName="FlashSale Admin" navGroups={NAV_GROUPS}>
              <Routes>
                <Route path="/dashboard"          element={<PrivateRoute role="ADMIN"><AdminDashboard /></PrivateRoute>} />
                <Route path="/categories"         element={<PrivateRoute role="ADMIN"><CategoryManagementPage /></PrivateRoute>} />
                <Route path="/users"              element={<PrivateRoute role="ADMIN"><UserManagementPage /></PrivateRoute>} />
                <Route path="/product-moderation" element={<PrivateRoute role="ADMIN"><ProductModerationPage /></PrivateRoute>} />
                <Route path="/refunds"            element={<PrivateRoute role="ADMIN"><RefundsPage /></PrivateRoute>} />
                <Route path="/flash-sale-config"  element={<PrivateRoute role="ADMIN"><FlashSaleConfigPage /></PrivateRoute>} />
                <Route path="/sellers-stripe"     element={<PrivateRoute role="ADMIN"><SellerStripePage /></PrivateRoute>} />
                <Route path="/banners"            element={<PrivateRoute role="ADMIN"><BannerManagementPage /></PrivateRoute>} />

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
