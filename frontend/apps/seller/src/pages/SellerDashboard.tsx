import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { sellerApi } from '@shared/api/seller.api';
import { FlashSaleSession, flashSaleApi } from '@shared/api/flashSale.api';
import { orderApi, type SellerOrderSummary } from '@shared/api/order.api';
import { Skeleton, Badge } from '@shared/components/ui';
import { fmtVnd, fmtDateTime } from '@shared/utils/format';
import { OrderStatusBadge } from '@/lib/orderStatus';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';
const fmtNum = (n: number) => n.toLocaleString('vi-VN');

function shortTime(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

export default function SellerDashboard() {
  const { data: stats, isLoading: statsLoading, error: statsError } = useQuery({
    queryKey: ['seller-dashboard-stats'],
    queryFn: () => sellerApi.getDashboardStats().then(r => r.data.data),
    retry: 1,
    refetchInterval: 60_000, // auto refresh every minute
  });

  // Recent orders for the dashboard
  const { data: recentOrdersData, isLoading: ordersLoading } = useQuery({
    queryKey: ['seller-dashboard-recent-orders'],
    queryFn: () => orderApi.getSellerOrders({ page: 0, size: 5 }).then(r => r.data.data),
    retry: 1,
  });
  const recentOrders: SellerOrderSummary[] = recentOrdersData?.content ?? [];

  // Upcoming flash sale sessions
  const { data: sessions = [] } = useQuery({
    queryKey: ['seller-dashboard-flash-sales'],
    queryFn: () => flashSaleApi.getSessions().then(r => r.data.data?.content ?? []),
    staleTime: 30_000,
  });
  const upcomingSessions: FlashSaleSession[] = sessions.filter(s => s.status === 'UPCOMING');
  const activeSessions: FlashSaleSession[] = sessions.filter(s => s.status === 'ACTIVE');

  const pendingOrders = stats?.pendingOrders ?? 0;
  const hasNoProducts = !statsLoading && stats && stats.totalProducts === 0;

  const displayStats = [
    {
      label: 'Tổng sản phẩm',
      value: statsLoading ? null : (stats?.totalProducts?.toLocaleString('vi-VN') ?? '—'),
      icon: '📦',
      gradient: 'from-blue-500 to-blue-600',
      detail: stats ? `${stats.activeProducts} đang bán` : null,
    },
    {
      label: 'Đơn hàng hôm nay',
      value: statsLoading ? null : (stats?.ordersToday?.toLocaleString('vi-VN') ?? '—'),
      icon: '🛒',
      gradient: 'from-green-500 to-green-600',
      detail: stats && stats.pendingOrders > 0 ? `${stats.pendingOrders} chờ xử lý` : null,
    },
    {
      label: 'Doanh thu tháng này',
      value: statsLoading ? null : (stats ? fmt(stats.revenueMonth) : '—'),
      icon: '💰',
      gradient: 'from-purple-500 to-purple-600',
      detail: stats ? `Từ ${fmtNum(stats.ordersToday)} đơn` : null,
    },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard Người Bán</h1>
        <p className="text-gray-500 mt-1 text-sm">Tổng quan hoạt động cửa hàng của bạn</p>
      </div>

      {/* Error banner */}
      {statsError && (
        <div className="mb-6 bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          Không thể tải thống kê. Vui lòng thử lại sau.
        </div>
      )}

      {/* Stats grid */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        {displayStats.map(({ label, value, icon, gradient, detail }) => (
          <div key={label} className="bg-white rounded-2xl border border-gray-100 p-5 hover:shadow-sm transition-shadow">
            <div className="flex items-start justify-between mb-3">
              <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${gradient} flex items-center justify-center text-lg shadow-sm`}>
                {icon}
              </div>
            </div>
            {statsLoading ? (
              <Skeleton className="h-8 w-24 rounded-lg mb-1" />
            ) : (
              <p className="text-2xl font-bold text-gray-900 mb-0.5">{value}</p>
            )}
            <p className="text-sm text-gray-500">{label}</p>
            {detail && <p className="text-xs text-gray-400 mt-0.5">{detail}</p>}
          </div>
        ))}
      </div>

      {/* Pending orders alert */}
      {pendingOrders > 0 && (
        <div className="mb-6 bg-amber-50 border border-amber-200 rounded-2xl p-4 flex items-center gap-3">
          <span className="text-2xl">⚠️</span>
          <div className="flex-1">
            <p className="font-semibold text-amber-900">
              Bạn có {pendingOrders} đơn hàng chờ xác nhận
            </p>
            <p className="text-sm text-amber-700">Cập nhật mã vận đơn để đơn hàng được giao cho khách.</p>
          </div>
          <Link to="/orders?filter=paid"
            className="shrink-0 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-sm font-medium rounded-xl transition-colors">
            Xử lý ngay
          </Link>
        </div>
      )}

      {/* Flash Sale status */}
      {activeSessions.length > 0 && (
        <div className="mb-6 bg-gradient-to-r from-orange-50 to-red-50 border border-orange-200 rounded-2xl p-4 flex items-center gap-3">
          <span className="text-2xl">⚡</span>
          <div className="flex-1">
            <p className="font-semibold text-orange-900">Đang có Flash Sale!</p>
            <p className="text-sm text-orange-700">
              {activeSessions[0].name} — kết thúc lúc {shortTime(activeSessions[0].endTime)}
            </p>
          </div>
          <Link to="/flash-sales"
            className="shrink-0 px-4 py-2 bg-orange-600 hover:bg-orange-700 text-white text-sm font-medium rounded-xl transition-colors">
            Xem chi tiết
          </Link>
        </div>
      )}

      {/* Upcoming Flash Sale */}
      {upcomingSessions.length > 0 && (
        <div className="mb-6 bg-gradient-to-r from-violet-50 to-indigo-50 border border-violet-200 rounded-2xl p-4 flex items-center gap-3">
          <span className="text-2xl">📅</span>
          <div className="flex-1">
            <p className="font-semibold text-violet-900">Flash Sale sắp diễn ra</p>
            <p className="text-sm text-violet-700">
              {upcomingSessions[0].name} · bắt đầu {fmtDateTime(upcomingSessions[0].startTime)}
            </p>
          </div>
          <Link to="/flash-sales"
            className="shrink-0 px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white text-sm font-medium rounded-xl transition-colors">
            Đăng ký ngay
          </Link>
        </div>
      )}

      {/* Getting started */}
      {hasNoProducts && !statsLoading && (
        <div className="bg-gradient-to-r from-blue-50 to-violet-50 border border-blue-100 rounded-2xl p-6 flex items-start gap-4 mb-6">
          <span className="text-3xl shrink-0">🚀</span>
          <div>
            <h3 className="font-semibold text-gray-900 mb-1">Bắt đầu bán hàng ngay!</h3>
            <p className="text-sm text-gray-600 mb-3">
              Đăng sản phẩm đầu tiên và kết nối Stripe để bắt đầu nhận thanh toán.
            </p>
            <div className="flex gap-2 flex-wrap">
              <Link to="/products"
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-xl transition-colors">
                Thêm sản phẩm
              </Link>
              <Link to="/stripe-onboarding"
                className="px-4 py-2 border border-blue-200 text-blue-700 hover:bg-blue-50 text-sm font-medium rounded-xl transition-colors">
                Kết nối Stripe
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* Main content grid: Recent orders + Quick actions */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent orders */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h2 className="font-semibold text-gray-900">📋 Đơn hàng gần đây</h2>
            <Link to="/orders" className="text-xs text-blue-600 hover:text-blue-700 font-medium">
              Xem tất cả →
            </Link>
          </div>

          {ordersLoading ? (
            <div className="divide-y divide-gray-50">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-4 px-5 py-3.5">
                  <Skeleton className="h-8 w-8 rounded-lg" />
                  <div className="flex-1 space-y-1.5">
                    <Skeleton className="h-3.5 w-1/3" />
                    <Skeleton className="h-3 w-1/5" />
                  </div>
                  <Skeleton className="h-5 w-16 rounded-full" />
                </div>
              ))}
            </div>
          ) : recentOrders.length === 0 ? (
            <div className="p-8 text-center text-sm text-gray-400">
              Chưa có đơn hàng nào.
            </div>
          ) : (
            <div className="divide-y divide-gray-50">
              {recentOrders.map(order => (
                <Link
                  key={order.orderId}
                  to={`/orders/${order.orderId}`}
                  className="flex items-center gap-3 px-5 py-3.5 hover:bg-gray-50/50 transition-colors"
                >
                  <div className="w-8 h-8 rounded-lg bg-gray-100 flex items-center justify-center text-sm shrink-0">
                    {order.isFlashSale ? '⚡' : '📦'}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-sm font-medium text-gray-900">{order.orderCode}</span>
                      {order.isFlashSale && (
                        <span className="text-[10px] bg-orange-100 text-orange-600 px-1.5 py-0.5 rounded font-medium">FS</span>
                      )}
                    </div>
                    <p className="text-xs text-gray-400">
                      {order.buyerName || `User #${order.buyerId}`} · {order.itemCount} sp · {fmtVnd(order.finalAmt)}
                    </p>
                  </div>
                  <OrderStatusBadge status={order.status} />
                </Link>
              ))}
            </div>
          )}
        </div>

        {/* Quick actions */}
        <div className="bg-white rounded-2xl border border-gray-100 p-5">
          <h2 className="font-semibold text-gray-900 mb-4">⚡ Thao tác nhanh</h2>
          <div className="space-y-3">
            {[
              { label: 'Thêm sản phẩm', icon: '➕', desc: 'Đăng sản phẩm mới', to: '/products', color: 'border-blue-200 hover:border-blue-400 hover:bg-blue-50' },
              { label: 'Đăng ký Flash Sale', icon: '⚡', desc: upcomingSessions.length > 0 ? `${upcomingSessions.length} phiên sắp tới` : 'Tham gia chương trình', to: '/flash-sales', color: 'border-violet-200 hover:border-violet-400 hover:bg-violet-50' },
              { label: 'Xử lý đơn hàng', icon: '📋', desc: pendingOrders > 0 ? `${pendingOrders} đơn chờ` : 'Quản lý đơn hàng', to: '/orders', color: 'border-green-200 hover:border-green-400 hover:bg-green-50' },
              { label: 'Xem thu nhập', icon: '💰', desc: 'Theo dõi doanh thu Stripe', to: '/payments', color: 'border-amber-200 hover:border-amber-400 hover:bg-amber-50' },
            ].map(({ label, icon, desc, to, color }) => (
              <Link
                key={label}
                to={to}
                className={`group flex items-center gap-3 bg-white rounded-xl border-2 p-3.5 transition-all ${color}`}
              >
                <span className="text-xl">{icon}</span>
                <div>
                  <p className="text-sm font-semibold text-gray-900">{label}</p>
                  <p className="text-xs text-gray-500">{desc}</p>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
