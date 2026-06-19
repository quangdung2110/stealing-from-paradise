const TAB_STATUS = [
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Đã từ chối' },
];

interface ProductModerationTabsProps {
  tab: string;
  onTabChange: (tab: string) => void;
  counts?: Record<string, number>;
}

export default function ProductModerationTabs({
  tab,
  onTabChange,
  counts,
}: ProductModerationTabsProps) {
  return (
    <div className="flex gap-2 mb-5">
      {TAB_STATUS.map((t) => (
        <button
          key={t.value}
          onClick={() => onTabChange(t.value)}
          className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
            tab === t.value
              ? 'bg-blue-600 text-white border-blue-600'
              : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
          }`}
        >
          {t.label}
          {counts?.[t.value] !== undefined && (
            <span className="ml-1.5 px-1.5 py-0.5 rounded-full text-xs bg-white/30">
              {counts[t.value]}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
