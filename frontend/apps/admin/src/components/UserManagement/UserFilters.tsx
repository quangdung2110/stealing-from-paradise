interface UserFiltersProps {
  searchQuery: string;
  onSearchQueryChange: (q: string) => void;
  roleFilter: string;
  onRoleFilterChange: (r: string) => void;
  statusFilter: string;
  onStatusFilterChange: (s: string) => void;
}

export default function UserFilters({
  searchQuery,
  onSearchQueryChange,
  roleFilter,
  onRoleFilterChange,
  statusFilter,
  onStatusFilterChange,
}: UserFiltersProps) {
  const roleFilters = [
    { value: '', label: 'Tất cả' },
    { value: 'BUYER', label: 'Người mua' },
    { value: 'SELLER', label: 'Người bán' },
    { value: 'ADMIN', label: 'Quản trị viên' },
  ];

  const statusFilters = [
    { value: '', label: 'Tất cả trạng thái' },
    { value: 'ACTIVE', label: 'Hoạt động' },
    { value: 'LOCKED', label: 'Đã khoá' },
    { value: 'BANNED', label: 'Đã cấm' },
  ];

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-4 mb-4 flex flex-wrap gap-3 items-center">
      <input
        type="text"
        value={searchQuery}
        onChange={(e) => onSearchQueryChange(e.target.value)}
        placeholder="Tìm theo tên, email..."
        className="flex-1 min-w-48 px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      {roleFilters.map((r) => (
        <button
          key={r.value}
          onClick={() => onRoleFilterChange(r.value)}
          className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-all ${
            roleFilter === r.value
              ? 'bg-blue-600 text-white border-blue-600'
              : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
          }`}
        >
          {r.label}
        </button>
      ))}
      {statusFilters.map((s) => (
        <button
          key={s.value}
          onClick={() => onStatusFilterChange(s.value)}
          className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-all ${
            statusFilter === s.value
              ? 'bg-blue-600 text-white border-blue-600'
              : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
          }`}
        >
          {s.label}
        </button>
      ))}
    </div>
  );
}
