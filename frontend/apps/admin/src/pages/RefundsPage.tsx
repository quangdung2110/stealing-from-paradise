import { useState, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { adminRefundApi, type RefundResponse } from '@shared/api/refund.api';
import ApproveRefundModal from '@/components/Refunds/ApproveRefundModal';
import RejectRefundModal from '@/components/Refunds/RejectRefundModal';
import RefundDetailDrawer from '@/components/Refunds/RefundDetailDrawer';
import RefundFilters, { type RefundStatus, type RefundType } from '@/components/Refunds/RefundFilters';
import RefundsTable from '@/components/Refunds/RefundsTable';
import Pagination from '@shared/components/Pagination';
import { EmptyState, Skeleton } from '@shared/components/ui';

// ─── Main Page ────────────────────────────────────────────────────────────────
export default function RefundsPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<RefundStatus>('ALL');
  const [typeFilter, setTypeFilter] = useState<RefundType>('ALL');
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [approveRefund, setApproveRefund] = useState<RefundResponse | null>(null);
  const [rejectRefund, setRejectRefund] = useState<RefundResponse | null>(null);
  const [detailRefund, setDetailRefund] = useState<RefundResponse | null>(null);

  // Debounce search: wait 400ms after typing before filtering, reset to page 0
  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedSearch(searchQuery.trim());
      setPage(0);
    }, 400);
    return () => clearTimeout(t);
  }, [searchQuery]);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-refunds', statusFilter, typeFilter, page],
    queryFn: () =>
      adminRefundApi.list({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        type: typeFilter === 'ALL' ? undefined : typeFilter,
        page,
        size: 20,
      }).then(r => r.data.data),
    retry: 1,
  });

  const refunds: RefundResponse[] = data?.content ?? [];
  // Client-side search filter
  const filteredRefunds = debouncedSearch
    ? refunds.filter(r =>
        String(r.refundId).includes(debouncedSearch) ||
        String(r.orderId).includes(debouncedSearch) ||
        r.reason?.toLowerCase().includes(debouncedSearch.toLowerCase())
      )
    : refunds;
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản lý hoàn tiền</h1>
          <p className="text-sm text-gray-500 mt-1">
            {totalElements > 0 && <span>{totalElements} yêu cầu</span>}
          </p>
        </div>
        <button
          onClick={() => queryClient.invalidateQueries({ queryKey: ['admin-refunds'] })}
          className="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 text-gray-600"
        >
          🔄 Làm mới
        </button>
      </div>

      {/* Search */}
      <div className="mb-4">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Tìm theo mã hoàn tiền, đơn hàng..."
          className="w-full max-w-md px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* Filters */}
      <RefundFilters
        statusFilter={statusFilter}
        onStatusFilterChange={(s) => { setStatusFilter(s); setPage(0); }}
        typeFilter={typeFilter}
        onTypeFilterChange={(t) => { setTypeFilter(t); setPage(0); }}
      />

      {/* Loading */}
      {isLoading && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm">
          <div className="grid grid-cols-[90px_100px_90px_120px_100px_1.5fr_110px_120px_110px] gap-4 border-b border-gray-100 bg-gray-50 px-4 py-3.5">
            {Array.from({ length: 9 }).map((_, i) => (
              <Skeleton key={i} className="h-3 w-full" />
            ))}
          </div>
          <div className="divide-y divide-gray-50">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="grid grid-cols-[90px_100px_90px_120px_100px_1.5fr_110px_120px_110px] items-center gap-4 px-4 py-4">
                <Skeleton className="h-4 w-14" />
                <Skeleton className="h-4 w-16" />
                <Skeleton className="h-6 w-16 rounded-full" />
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-6 w-16 rounded-full" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-6 w-20 rounded-full" />
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-4 w-20" />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          Không thể tải danh sách hoàn tiền. Vui lòng thử lại.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && filteredRefunds.length === 0 && (
        <EmptyState
          iconKey="refund"
          title="Không có yêu cầu hoàn tiền nào"
          description={
            debouncedSearch || statusFilter !== 'ALL' || typeFilter !== 'ALL'
              ? 'Thử đổi từ khóa hoặc bộ lọc để xem thêm kết quả.'
              : 'Các yêu cầu hoàn tiền mới sẽ xuất hiện ở đây để admin xử lý.'
          }
          className="bg-white rounded-2xl border border-gray-100"
        />
      )}

      {/* Table */}
      {!isLoading && !error && filteredRefunds.length > 0 && (
        <>
          <RefundsTable
            refunds={filteredRefunds}
            onDetail={setDetailRefund}
            onApprove={setApproveRefund}
            onReject={setRejectRefund}
          />

          {/* Pagination */}
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        </>
      )}

      {/* Modals */}
      {approveRefund && (
        <ApproveRefundModal
          refund={approveRefund}
          onClose={() => setApproveRefund(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-refunds'] })}
        />
      )}
      {rejectRefund && (
        <RejectRefundModal
          refund={rejectRefund}
          onClose={() => setRejectRefund(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-refunds'] })}
        />
      )}
      {detailRefund && (
        <RefundDetailDrawer
          refund={detailRefund}
          onClose={() => setDetailRefund(null)}
        />
      )}
    </div>
  );
}
