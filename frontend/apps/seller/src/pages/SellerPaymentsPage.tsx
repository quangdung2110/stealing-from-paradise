import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { sellerApi } from '@shared/api/seller.api';
import { Skeleton, Badge, Button } from '@shared/components/ui';
import { fmtVnd } from '@shared/utils/format';

const fmt = (n: number) => n.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' });
const fmtNum = (n: number) => n.toLocaleString('vi-VN');

const STATUS_CONFIG: Record<string, { bg: string; color: string; label: string; icon: string }> = {
  SUCCEEDED:       { bg: 'bg-green-50',  color: 'text-green-700',  label: 'Đã chuyển',         icon: '✓' },
  PENDING:         { bg: 'bg-yellow-50', color: 'text-yellow-700', label: 'Đang chờ',          icon: '⏳' },
  FAILED:          { bg: 'bg-red-50',    color: 'text-red-700',    label: 'Thất bại',          icon: '✗' },
  SKIPPED:         { bg: 'bg-gray-50',   color: 'text-gray-600',   label: 'Bị bỏ qua',         icon: '⊘' },
  REVERSED:        { bg: 'bg-orange-50', color: 'text-orange-700', label: 'Bị đảo ngược',      icon: '↩' },
  CANCELLED:       { bg: 'bg-gray-50',   color: 'text-gray-500',   label: 'Đã hủy',            icon: '⊘' },
};

function formatDate(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

type Tab = 'earnings' | 'stripe';

export default function SellerPaymentsPage() {
  const [tab, setTab] = useState<Tab>('earnings');
  const [dashError, setDashError] = useState<string | null>(null);
  const [transferPage, setTransferPage] = useState(0);
  const PAGE_SIZE = 10;

  const { data, isLoading, error } = useQuery({
    queryKey: ['seller-earnings'],
    queryFn: () => sellerApi.getEarnings().then(r => r.data.data),
    retry: 1,
    refetchInterval: 60_000,
  });

  const dashMut = useMutation({
    mutationFn: () => sellerApi.getStripeDashboardLink(),
    onSuccess: (res) => {
      const url = res.data.data?.dashboardUrl;
      if (url) window.open(url, '_blank', 'noopener,noreferrer');
    },
    onError: (err: any) => {
      setDashError(err?.response?.data?.message || 'Không thể mở Stripe Dashboard. Hãy đảm bảo bạn đã hoàn tất onboarding.');
    },
  });

  const st = data
    ? {
        total: data.totalEarnings,
        available: data.availableBalance,
        pending: data.pendingBalance,
        fee: data.platformFeePercentage,
        count: data.totalOrders,
      }
    : null;

  const allTransfers = data?.transfers ?? [];
  const paginatedTransfers = allTransfers.slice(transferPage * PAGE_SIZE, (transferPage + 1) * PAGE_SIZE);
  const totalTransferPages = Math.ceil(allTransfers.length / PAGE_SIZE);

  // Calculate monthly stats from transfers
  const monthlyStats = (() => {
    if (!allTransfers.length) return [];
    const byMonth: Record<string, { revenue: number; fee: number; count: number }> = {};
    allTransfers.forEach(t => {
      const m = t.createdAt?.slice(0, 7);
      if (!m) return;
      if (!byMonth[m]) byMonth[m] = { revenue: 0, fee: 0, count: 0 };
      byMonth[m].revenue += t.netAmount ?? (t.transferAmount - (t.feeAmount ?? 0));
      byMonth[m].fee += t.feeAmount ?? 0;
      byMonth[m].count += 1;
    });
    return Object.entries(byMonth)
      .sort(([a], [b]) => b.localeCompare(a))
      .slice(0, 6);
  })();

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">💰 Thanh toán & Thu nhập</h1>
        <p className="text-gray-500 mt-1 text-sm">Theo dõi thu nhập và quản lý tài khoản Stripe của bạn</p>
      </div>

      {/* Balance cards */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        {[
          {
            label: 'Tổng thu nhập', value: st ? fmtNum(Math.round(st.total)) : null,
            icon: '💰', gradient: 'from-violet-500 to-purple-600',
          },
          {
            label: 'Sẵn sàng nhận', value: st ? fmtNum(Math.round(st.available)) : null,
            icon: '✅', gradient: 'from-emerald-500 to-green-600', highlight: true,
          },
          {
            label: 'Đang xử lý', value: st ? fmtNum(Math.round(st.pending)) : null,
            icon: '⏳', gradient: 'from-amber-500 to-orange-500',
          },
        ].map(({ label, value, icon, gradient, highlight }) => (
          <div key={label}
            className={`bg-white rounded-2xl border p-5 ${highlight ? 'border-green-200 shadow-sm shadow-green-100' : 'border-gray-100'}`}>
            <div className="flex items-start justify-between mb-4">
              <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${gradient} flex items-center justify-center text-lg shadow-sm`}>
                {icon}
              </div>
            </div>
            {isLoading ? (
              <Skeleton className="h-8 w-24 rounded-lg mb-1" />
            ) : (
              <p className={`text-2xl font-bold text-gray-900 mb-1 ${highlight ? 'text-green-700' : ''}`}>
                {value ?? '—'}
                {!isLoading && value && <span className="text-sm font-normal text-gray-400 ml-1">VND</span>}
              </p>
            )}
            <p className="text-sm text-gray-500">{label}</p>
          </div>
        ))}
      </div>

      {/* Fee & orders info */}
      {st && (
        <div className="flex items-center gap-6 mb-6 text-sm text-gray-500 flex-wrap">
          <span><span className="font-medium text-gray-700">{st.fee}%</span> phí nền tảng</span>
          <span className="text-gray-200">|</span>
          <span><span className="font-medium text-gray-700">{fmtNum(st.count)}</span> giao dịch</span>
          <span className="text-gray-200">|</span>
          <span>Thanh toán qua <span className="font-medium text-violet-600">Stripe</span></span>
        </div>
      )}

      {/* Monthly stats */}
      {monthlyStats.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
          <h2 className="font-semibold text-gray-900 text-sm mb-4">📊 Thu nhập theo tháng</h2>
          <div className="flex items-end gap-3 h-32">
            {monthlyStats.map(([month, stat]) => {
              const maxRevenue = Math.max(...monthlyStats.map(([, s]) => s.revenue), 1);
              const heightPct = Math.max((stat.revenue / maxRevenue) * 100, 5);
              return (
                <div key={month} className="flex-1 flex flex-col items-center gap-1">
                  <span className="text-xs font-semibold text-gray-900">{fmtVnd(stat.revenue)}</span>
                  <div className="w-full bg-gradient-to-t from-violet-500 to-purple-500 rounded-lg transition-all"
                    style={{ height: `${heightPct}%`, minHeight: '8px' }} />
                  <span className="text-[10px] text-gray-400">{month.slice(5, 7)}/{month.slice(2, 4)}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 mb-6 bg-gray-100 p-1 rounded-xl w-fit">
        <button onClick={() => setTab('earnings')}
          className={`px-5 py-2 rounded-lg text-sm font-medium transition-all ${
            tab === 'earnings' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
          }`}>
          Lịch sử thu nhập
        </button>
        <button onClick={() => setTab('stripe')}
          className={`px-5 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2 ${
            tab === 'stripe' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
          }`}>
          Stripe Dashboard
        </button>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-6">
          Không thể tải dữ liệu thanh toán. Vui lòng thử lại.
        </div>
      )}

      {/* Earnings table */}
      {tab === 'earnings' && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-50 flex items-center justify-between">
            <h2 className="font-bold text-gray-900">Lịch sử thu nhập</h2>
            <span className="text-sm text-gray-400">{allTransfers.length} giao dịch</span>
          </div>

          {isLoading ? (
            <div className="divide-y divide-gray-50">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-4 px-5 py-4">
                  <Skeleton className="h-9 w-9 rounded-xl" />
                  <div className="flex-1 space-y-2"><Skeleton className="h-4 w-1/3" /><Skeleton className="h-3 w-1/4" /></div>
                  <Skeleton className="h-5 w-24" />
                </div>
              ))}
            </div>
          ) : !allTransfers.length ? (
            <div className="p-12 text-center">
              <div className="text-5xl mb-4">💸</div>
              <h3 className="font-semibold text-gray-900 mb-2">Chưa có thu nhập nào</h3>
              <p className="text-sm text-gray-500 mb-6">
                Khi khách hàng thanh toán đơn hàng của bạn, thu nhập sẽ xuất hiện tại đây.
              </p>
              <div className="flex justify-center gap-3">
                <a href="/products" className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-xl transition-colors">Thêm sản phẩm</a>
                <a href="/stripe-onboarding" className="px-4 py-2 border border-blue-200 text-blue-700 hover:bg-blue-50 text-sm font-medium rounded-xl transition-colors">Kết nối Stripe</a>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
                    <th className="px-5 py-3 text-left font-medium">Mã đơn</th>
                    <th className="px-4 py-3 text-right font-medium">Số tiền</th>
                    <th className="px-4 py-3 text-right font-medium">Phí</th>
                    <th className="px-4 py-3 text-right font-medium">Thu nhập</th>
                    <th className="px-4 py-3 text-center font-medium">Trạng thái</th>
                    <th className="px-5 py-3 text-left font-medium">Ngày</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {paginatedTransfers.map((t) => {
                    const cfg = STATUS_CONFIG[t.status] ?? STATUS_CONFIG.PENDING;
                    const transferAmt = t.transferAmount ?? 0;
                    const feeAmount = t.feeAmount ?? Math.round(transferAmt * ((data?.platformFeePercentage ?? 0) / 100));
                    const netAmount = t.netAmount ?? transferAmt - feeAmount;
                    return (
                      <tr key={t.id} className="hover:bg-gray-50 transition-colors">
                        <td className="px-5 py-4">
                          <span className="font-mono text-gray-900">#{t.orderId || '—'}</span>
                        </td>
                        <td className="px-4 py-4 text-right text-gray-600">{fmt(transferAmt)}</td>
                        <td className="px-4 py-4 text-right text-red-500">{feeAmount > 0 ? `-${fmt(feeAmount)}` : fmt(feeAmount)}</td>
                        <td className="px-4 py-4 text-right font-bold text-gray-900">{fmt(netAmount)}</td>
                        <td className="px-4 py-4 text-center">
                          <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium ${cfg.bg} ${cfg.color}`}>
                            {cfg.icon} {cfg.label}
                          </span>
                        </td>
                        <td className="px-5 py-4 text-gray-500 text-xs whitespace-nowrap">{formatDate(t.createdAt)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {/* Pagination */}
              {totalTransferPages > 1 && (
                <div className="flex items-center justify-center gap-2 px-5 py-4 border-t border-gray-50">
                  <button onClick={() => setTransferPage(p => Math.max(0, p - 1))} disabled={transferPage === 0}
                    className="px-3 py-1.5 text-xs border rounded-lg disabled:opacity-40 hover:bg-gray-50">← Trước</button>
                  <span className="text-xs text-gray-500">{transferPage + 1} / {totalTransferPages}</span>
                  <button onClick={() => setTransferPage(p => Math.min(totalTransferPages - 1, p + 1))} disabled={transferPage >= totalTransferPages - 1}
                    className="px-3 py-1.5 text-xs border rounded-lg disabled:opacity-40 hover:bg-gray-50">Sau →</button>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Stripe Dashboard tab */}
      {tab === 'stripe' && (
        <div className="space-y-6">
          <div className="bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-100 rounded-2xl p-6 flex items-start gap-4">
            <span className="text-4xl shrink-0">🔗</span>
            <div>
              <h3 className="font-semibold text-gray-900 mb-1">Quản lý qua Stripe Dashboard</h3>
              <p className="text-sm text-gray-600">
                Nhấn nút bên dưới để mở Stripe Dashboard — nơi bạn có thể xem số dư,
                lịch sử giao dịch chi tiết, tạo lệnh rút tiền, cập nhật thông tin tài khoản ngân hàng.
              </p>
            </div>
          </div>

          {dashError && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
              <p className="mb-2">{dashError}</p>
              <div className="flex gap-2 flex-wrap">
                <button onClick={() => setDashError(null)} className="underline font-medium">Đóng</button>
                <a href="/stripe-onboarding" className="underline font-medium text-red-700">Đi tới trang kết nối Stripe →</a>
              </div>
            </div>
          )}

          <div className="bg-white rounded-2xl border border-gray-100 p-6 text-center">
            <div className="text-5xl mb-4">💳</div>
            <h3 className="font-bold text-gray-900 mb-2">Mở Stripe Dashboard</h3>
            <p className="text-sm text-gray-500 mb-5 max-w-sm mx-auto">
              Liên kết đăng nhập có hiệu lực trong 30 phút.
            </p>
            <Button onClick={() => dashMut.mutate()} loading={dashMut.isPending} variant="primary">
              ↗ Mở Stripe Dashboard
            </Button>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
            {[
              { icon: '💵', label: 'Số dư & Thu nhập', desc: 'Xem số dư khả dụng và đang xử lý' },
              { icon: '🏦', label: 'Rút tiền', desc: 'Chuyển tiền về tài khoản ngân hàng VN' },
              { icon: '📜', label: 'Lịch sử GD', desc: 'Chi tiết mọi giao dịch Stripe' },
              { icon: '⚙️', label: 'Cài đặt TK', desc: 'Thông tin tài khoản & ngân hàng' },
              { icon: '📊', label: 'Báo cáo', desc: 'Thống kê doanh thu theo ngày/tháng' },
              { icon: '🔔', label: 'Thông báo', desc: 'Cài đặt email & SMS alerts' },
            ].map(({ icon, label, desc }) => (
              <div key={label} className="bg-white rounded-2xl border border-gray-100 p-4 text-center hover:border-gray-200 transition-colors">
                <span className="text-3xl block mb-2">{icon}</span>
                <h4 className="font-semibold text-gray-900 text-sm mb-1">{label}</h4>
                <p className="text-xs text-gray-500">{desc}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
