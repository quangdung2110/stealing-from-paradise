interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export default function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  const canGoPrevious = page > 0;
  const canGoNext = page < totalPages - 1;
  const visiblePages = Array.from(new Set([
    0,
    page - 1,
    page,
    page + 1,
    totalPages - 1,
  ]))
    .filter((p) => p >= 0 && p < totalPages)
    .sort((a, b) => a - b);

  return (
    <nav className="mt-6 flex flex-wrap items-center justify-center gap-2" aria-label="Phân trang">
      <button
        onClick={() => onPageChange(page - 1)}
        disabled={!canGoPrevious}
        className="rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
      >
        Trước
      </button>
      <div className="flex items-center gap-1 rounded-xl border border-gray-200 bg-white p-1 shadow-sm">
        {visiblePages.map((p, index) => (
          <span key={p} className="flex items-center gap-1">
            {index > 0 && p - visiblePages[index - 1] > 1 && (
              <span className="px-2 text-sm text-gray-400">...</span>
            )}
            <button
              onClick={() => onPageChange(p)}
              aria-current={p === page ? 'page' : undefined}
              className={`min-w-9 rounded-lg px-3 py-1.5 text-sm font-semibold transition-colors ${
                p === page
                  ? 'bg-blue-600 text-white'
                  : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              {p + 1}
            </button>
          </span>
        ))}
      </div>
      <button
        onClick={() => onPageChange(page + 1)}
        disabled={!canGoNext}
        className="rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
      >
        Sau
      </button>
    </nav>
  );
}
