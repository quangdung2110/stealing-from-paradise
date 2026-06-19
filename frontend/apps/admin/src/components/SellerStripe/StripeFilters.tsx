interface StripeFiltersProps {
  searchQuery: string;
  onSearchQueryChange: (q: string) => void;
  statusFilter: string;
  onStatusFilterChange: (s: string) => void;
}

export default function StripeFilters({
  searchQuery,
  onSearchQueryChange,
  statusFilter,
  onStatusFilterChange,
}: StripeFiltersProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-200/80 p-5 mb-8 shadow-sm flex flex-col md:flex-row gap-4 items-center justify-between">
      <div className="relative w-full md:w-96">
        <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400 text-sm">
          🔍
        </span>
        <input
          type="text"
          placeholder="Tìm theo Seller ID hoặc Stripe Account ID..."
          value={searchQuery}
          onChange={(e) => onSearchQueryChange(e.target.value)}
          className="w-full pl-9 pr-4 py-2.5 text-sm bg-gray-50 hover:bg-gray-100/70 focus:bg-white border border-gray-200 rounded-xl transition-all focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
        />
      </div>

      <div className="flex gap-3 w-full md:w-auto">
        <select
          value={statusFilter}
          onChange={(e) => onStatusFilterChange(e.target.value)}
          className="w-full md:w-52 px-4 py-2.5 text-sm bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
        >
          <option value="">Tất cả trạng thái</option>
          <option value="COMPLETE">Hoàn thành</option>
          <option value="IN_PROGRESS">Đang KYC</option>
          <option value="PENDING">Chưa bắt đầu</option>
          <option value="SUSPENDED">Bị hạn chế</option>
          <option value="NEEDS_ACTION">Cần xử lý</option>
        </select>
      </div>
    </div>
  );
}
