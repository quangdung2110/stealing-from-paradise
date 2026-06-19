import { useQuery } from '@tanstack/react-query';
import apiClient from '@shared/lib/axios';
import type { ApiResponse, PageResponse } from '@shared/types/api';
import type { SellerProduct } from '@shared/api/seller.api';

const STATUS_TABS = [
  { value: '', label: 'Tất cả', statusKey: '' },
  { value: 'APPROVED', label: 'Đã duyệt', statusKey: 'APPROVED' },
  { value: 'ACTIVE', label: 'Đang bán', statusKey: 'ACTIVE' },
  { value: 'INACTIVE', label: 'Đã ẩn', statusKey: 'INACTIVE' },
  { value: 'PENDING', label: 'Chờ duyệt', statusKey: 'PENDING' },
  { value: 'REJECTED', label: 'Từ chối', statusKey: 'REJECTED' },
  { value: 'DRAFT', label: 'Nháp', statusKey: 'DRAFT' },
  { value: 'OUT_OF_STOCK', label: 'Hết hàng', statusKey: 'OUT_OF_STOCK' },
];

interface ProductTabsProps {
  statusFilter: string;
  onStatusFilterChange: (status: string) => void;
  searchQuery: string;
  onSearchQueryChange: (q: string) => void;
}

export default function ProductTabs({
  statusFilter,
  onStatusFilterChange,
  searchQuery,
  onSearchQueryChange,
}: ProductTabsProps) {
  // Fetch count per status for badge
  const { data: counts } = useQuery({
    queryKey: ['seller-product-counts'],
    queryFn: async () => {
      const results: Record<string, number> = { '': 0 };
      for (const tab of STATUS_TABS) {
        if (tab.value === '') {
          const r = await apiClient.get<ApiResponse<PageResponse<SellerProduct>>>('/sellers/me/products', {
            params: { page: 0, size: 1 },
          });
          results[''] = r.data.data?.totalElements ?? 0;
        } else {
          const r = await apiClient.get<ApiResponse<PageResponse<SellerProduct>>>('/sellers/me/products', {
            params: { status: tab.statusKey, page: 0, size: 1 },
          });
          results[tab.value] = r.data.data?.totalElements ?? 0;
        }
      }
      return results;
    },
    staleTime: 30_000,
    retry: false,
  });

  return (
    <div className="flex gap-4 mb-5 flex-wrap items-center">
      <div className="flex gap-2 flex-wrap">
        {STATUS_TABS.map((tab) => {
          const count = counts?.[tab.value];
          return (
            <button
              key={tab.value}
              onClick={() => onStatusFilterChange(tab.value)}
              className={`inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
                statusFilter === tab.value
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
              }`}
            >
              {tab.label}
              {count !== undefined && (
                <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${
                  statusFilter === tab.value ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-500'
                }`}>
                  {count}
                </span>
              )}
            </button>
          );
        })}
      </div>
      <input
        type="text"
        value={searchQuery}
        onChange={(e) => onSearchQueryChange(e.target.value)}
        placeholder="🔍 Tìm sản phẩm..."
        className="px-4 py-1.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-[180px]"
      />
    </div>
  );
}
