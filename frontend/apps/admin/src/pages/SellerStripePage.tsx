import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { adminApi } from '@shared/api/admin.api';
import StripeStatsCards from '@/components/SellerStripe/StripeStatsCards';
import StripeFilters from '@/components/SellerStripe/StripeFilters';
import StripeAccountsTable from '@/components/SellerStripe/StripeAccountsTable';
import { Skeleton } from '@shared/components/ui';

const needsStripeAction = (acc: {
  onboardingStatus: string;
  detailsSubmitted: boolean;
  chargesEnabled: boolean;
  payoutsEnabled: boolean;
}) =>
  acc.onboardingStatus === 'SUSPENDED' ||
  !acc.detailsSubmitted ||
  !acc.chargesEnabled ||
  !acc.payoutsEnabled;

export default function SellerStripePage() {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-seller-stripe-accounts'],
    queryFn: () => adminApi.getSellerStripeAccounts().then(r => r.data.data),
    retry: 1,
  });

  const summary = data?.summary || {
    totalSellers: 0,
    completedSellers: 0,
    pendingSellers: 0,
    inProgressSellers: 0,
    suspendedSellers: 0,
  };

  const accounts = data?.accounts || [];
  const actionableCount = accounts.filter(needsStripeAction).length;

  const filteredAccounts = accounts.filter((acc) => {
    const statusMatch =
      !statusFilter ||
      (statusFilter === 'NEEDS_ACTION' ? needsStripeAction(acc) : acc.onboardingStatus === statusFilter);
    const query = searchQuery.trim().toLowerCase();
    const searchMatch =
      !query ||
      String(acc.sellerId).includes(query) ||
      acc.stripeAccountId.toLowerCase().includes(query) ||
      acc.accountStatus.toLowerCase().includes(query);
    return statusMatch && searchMatch;
  });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-8">
        <div>
          <h1 className="text-3xl font-extrabold text-gray-900 tracking-tight bg-clip-text bg-gradient-to-r from-gray-900 to-indigo-950">
            Quản lý Đối tác Seller (Stripe)
          </h1>
          <p className="text-gray-500 mt-1.5 text-sm">
            Theo dõi tiến trình onboarding, xác minh thông tin KYC và trạng thái tài khoản thanh toán Stripe Connect của các nhà bán hàng.
          </p>
        </div>
        <button
          onClick={() => refetch()}
          className="mt-4 md:mt-0 px-4.5 py-2 border border-gray-300 text-gray-700 hover:bg-gray-50 font-medium rounded-xl text-sm transition-all shadow-sm hover:shadow active:scale-95 flex items-center gap-2"
        >
          🔄 Làm mới dữ liệu
        </button>
      </div>

      {actionableCount > 0 && (
        <div className="mb-6 flex flex-col gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-semibold">{actionableCount} seller cần kiểm tra Stripe</p>
            <p className="text-amber-700">Bao gồm tài khoản chưa KYC, chưa bật charges/payouts hoặc đang bị hạn chế.</p>
          </div>
          <button
            onClick={() => setStatusFilter('NEEDS_ACTION')}
            className="shrink-0 rounded-xl bg-amber-600 px-4 py-2 text-xs font-semibold text-white hover:bg-amber-700"
          >
            Xem cần xử lý
          </button>
        </div>
      )}

      {/* Stats Cards */}
      <StripeStatsCards summary={summary} />

      {/* Filter and Search */}
      <StripeFilters
        searchQuery={searchQuery}
        onSearchQueryChange={setSearchQuery}
        statusFilter={statusFilter}
        onStatusFilterChange={setStatusFilter}
      />

      {/* Main Content Table */}
      {isLoading ? (
        <div className="bg-white rounded-2xl border border-gray-200 p-5 shadow-sm">
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="grid grid-cols-[1fr_1fr_120px_120px_100px] gap-4">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-6 w-24 rounded-full" />
                <Skeleton className="h-6 w-24 rounded-full" />
                <Skeleton className="h-4 w-20" />
              </div>
            ))}
          </div>
        </div>
      ) : error ? (
        <div className="bg-rose-50 border border-rose-200 rounded-2xl p-6 text-center shadow-sm">
          <p className="font-bold text-rose-800 text-lg mb-1">Không thể tải dữ liệu</p>
          <p className="text-rose-600 text-sm mb-4">Lỗi: {(error as any)?.response?.data?.message || error.message}</p>
          <button
            onClick={() => refetch()}
            className="px-5 py-2.5 bg-rose-600 hover:bg-rose-700 text-white font-semibold rounded-xl text-sm transition-all active:scale-95 shadow-sm"
          >
            Thử lại
          </button>
        </div>
      ) : filteredAccounts.length === 0 ? (
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center shadow-sm">
          <div className="text-5xl mb-4 opacity-30">
            {searchQuery || statusFilter ? '🔍' : '🏪'}
          </div>
          <h3 className="font-bold text-gray-900 text-lg mb-1">
            {searchQuery || statusFilter ? 'Không tìm thấy kết quả' : 'Chưa có nhà bán hàng nào'}
          </h3>
          <p className="text-gray-400 text-sm mt-1 max-w-sm mx-auto">
            {searchQuery || statusFilter
              ? 'Thử thay đổi từ khóa tìm kiếm hoặc bộ lọc trạng thái để xem thêm kết quả.'
              : 'Khi seller liên kết tài khoản Stripe, họ sẽ xuất hiện ở đây để quản lý.'}
          </p>
          {(searchQuery || statusFilter) && (
            <button
              onClick={() => { setSearchQuery(''); setStatusFilter(''); }}
              className="mt-4 px-4 py-2 text-sm font-medium text-blue-600 bg-blue-50 hover:bg-blue-100 rounded-xl transition-colors"
            >
              Xoá bộ lọc
            </button>
          )}
        </div>
      ) : (
        <StripeAccountsTable
          filteredAccounts={filteredAccounts}
          accounts={accounts}
        />
      )}
    </div>
  );
}
