/**
 * SellerOrdersPage — UC-ORDER-007 "View Seller Orders".
 *
 * Paginated, status-filterable list of every order placed with this seller's
 * shop. Supports ?filter=<status> query param for deep-linking from Dashboard.
 */
import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { orderApi, type SellerOrderSummary, type OrderStatus } from '@shared/api/order.api';
import TrackingModal from '@/components/Orders/TrackingModal';
import RTSModal from '@/components/Orders/RTSModal';
import CancelOrderModal from '@/components/Orders/CancelOrderModal';
import OrderDrawer from '@/components/Orders/OrderDrawer';
import OrderFilters from '@/components/Orders/OrderFilters';
import OrdersTable from '@/components/Orders/OrdersTable';
import Pagination from '@shared/components/Pagination';
import { Skeleton, EmptyState } from '@shared/components/ui';

/** Build a human-friendly buyer label for modal headers. */
const buyerLabel = (o: SellerOrderSummary) => o.buyerName || o.buyerUsername || `User #${o.buyerId}`;

const FILTER_MAP: Record<string, OrderStatus> = {
  paid: 'PAID', pending: 'PENDING', shipping: 'SHIPPING',
  delivered: 'DELIVERED', cancelled: 'CANCELLED', refunded: 'REFUNDED',
};

export default function SellerOrdersPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [searchCode, setSearchCode] = useState('');

  // Read ?filter=<status> from URL (for dashboard deep-links)
  useEffect(() => {
    const f = searchParams.get('filter');
    if (f && FILTER_MAP[f]) {
      setFilter(FILTER_MAP[f]);
      // clear the param after reading
      const next = new URLSearchParams(searchParams);
      next.delete('filter');
      setSearchParams(next, { replace: true });
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const { data, isLoading, error } = useQuery({
    queryKey: ['seller-orders', filter, page, searchCode],
    queryFn: () =>
      orderApi.getSellerOrders({
        status: filter === 'ALL' ? undefined : filter,
        page,
        size: 20,
      }).then(r => r.data.data),
    retry: 1,
  });

  const orders: SellerOrderSummary[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  // Filter by order code search locally
  const filteredOrders = searchCode
    ? orders.filter(o => o.orderCode.toLowerCase().includes(searchCode.toLowerCase()))
    : orders;

  /** Refresh the list after any mutation succeeds. */
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['seller-orders'] });

  const [trackingOrder, setTrackingOrder] = useState<SellerOrderSummary | null>(null);
  const [rtsOrder, setRtsOrder] = useState<SellerOrderSummary | null>(null);
  const [cancelOrder, setCancelOrder] = useState<SellerOrderSummary | null>(null);
  const [drawerOrder, setDrawerOrder] = useState<SellerOrderSummary | null>(null);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Đơn hàng</h1>
          <p className="text-sm text-gray-500 mt-1">Quản lý và xử lý đơn hàng từ khách</p>
        </div>
        <div className="flex items-center gap-2">
          {/* Search by order code */}
          <input
            type="text"
            placeholder="Tìm mã đơn…"
            value={searchCode}
            onChange={e => setSearchCode(e.target.value)}
            className="w-40 px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
          />
          <button onClick={refresh}
            className="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 text-gray-600">
            🔄
          </button>
        </div>
      </div>

      {/* Filter bar */}
      <OrderFilters
        filter={filter}
        onFilterChange={(f) => { setFilter(f); setPage(0); }}
      />

      {/* Loading */}
      {isLoading && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden divide-y divide-gray-50">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="flex items-center gap-4 px-5 py-4">
              <Skeleton className="h-10 w-10 rounded-xl" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-1/3" />
                <Skeleton className="h-3 w-1/4" />
              </div>
              <Skeleton className="h-6 w-20 rounded-full" />
              <Skeleton className="h-4 w-16" />
            </div>
          ))}
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          Không thể tải đơn hàng. Vui lòng thử lại.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && filteredOrders.length === 0 && (
        <EmptyState
          iconKey="receipt"
          title={searchCode ? 'Không tìm thấy đơn hàng' : 'Chưa có đơn hàng nào'}
          description={searchCode ? `Không có mã "${searchCode}".` : 'Đơn hàng từ khách sẽ xuất hiện ở đây.'}
        />
      )}

      {/* Table */}
      {!isLoading && !error && filteredOrders.length > 0 && (
        <>
          <OrdersTable
            orders={filteredOrders}
            onViewDetail={setDrawerOrder}
            onShip={setTrackingOrder}
            onCancel={setCancelOrder}
            onReturnToSender={setRtsOrder}
          />
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      )}

      {/* Modals */}
      {trackingOrder && (
        <TrackingModal
          orderId={trackingOrder.orderId}
          orderCode={trackingOrder.orderCode}
          customerLabel={buyerLabel(trackingOrder)}
          onClose={() => setTrackingOrder(null)}
          onSuccess={refresh}
        />
      )}
      {cancelOrder && (
        <CancelOrderModal
          orderId={cancelOrder.orderId}
          orderCode={cancelOrder.orderCode}
          onClose={() => setCancelOrder(null)}
          onSuccess={refresh}
        />
      )}
      {rtsOrder && (
        <RTSModal
          orderId={rtsOrder.orderId}
          orderCode={rtsOrder.orderCode}
          onClose={() => setRtsOrder(null)}
          onSuccess={refresh}
        />
      )}
      {drawerOrder && (
        <OrderDrawer
          order={drawerOrder}
          onClose={() => setDrawerOrder(null)}
        />
      )}
    </div>
  );
}
