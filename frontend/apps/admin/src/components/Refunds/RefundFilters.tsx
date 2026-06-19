export type RefundStatus = 'ALL' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'REJECTED';
export type RefundType = 'ALL' | 'FULL' | 'PARTIAL';

const STATUS_FILTERS: { value: RefundStatus; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'SUCCESS', label: 'Đã hoàn' },
  { value: 'FAILED', label: 'Thất bại' },
  { value: 'REJECTED', label: 'Từ chối' },
];

const TYPE_FILTERS: { value: RefundType; label: string }[] = [
  { value: 'ALL', label: 'Tất cả loại' },
  { value: 'FULL', label: 'Hoàn toàn bộ' },
  { value: 'PARTIAL', label: 'Một phần' },
];

interface RefundFiltersProps {
  statusFilter: RefundStatus;
  onStatusFilterChange: (s: RefundStatus) => void;
  typeFilter: RefundType;
  onTypeFilterChange: (t: RefundType) => void;
}

export default function RefundFilters({
  statusFilter,
  onStatusFilterChange,
  typeFilter,
  onTypeFilterChange,
}: RefundFiltersProps) {
  return (
    <div className="flex gap-4 mb-5 flex-wrap">
      <div className="flex gap-2 flex-wrap">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => onStatusFilterChange(f.value)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
              statusFilter === f.value
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>
      <select
        value={typeFilter}
        onChange={(e) => onTypeFilterChange(e.target.value as RefundType)}
        className="px-3 py-1.5 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        {TYPE_FILTERS.map((f) => (
          <option key={f.value} value={f.value}>
            {f.label}
          </option>
        ))}
      </select>
    </div>
  );
}
