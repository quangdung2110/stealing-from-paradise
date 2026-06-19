import { useEffect, useRef, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { productApi, type ProductDetail } from '@shared/api/product.api';
import { flashSaleApi } from '@shared/api/flashSale.api';
import ProductCard from '@/components/ProductCard';

const SORT_OPTIONS = [
  { value: '', label: 'Mặc định' },
  { value: 'price_asc', label: 'Giá: Thấp → Cao' },
  { value: 'price_desc', label: 'Giá: Cao → Thấp' },
];

const PRICE_RANGES = [
  { label: 'Tất cả', min: undefined, max: undefined },
  { label: 'Dưới 100K', min: 0, max: 100000 },
  { label: '100K - 300K', min: 100000, max: 300000 },
  { label: '300K - 500K', min: 300000, max: 500000 },
  { label: '500K - 1M', min: 500000, max: 1000000 },
  { label: 'Trên 1M', min: 1000000, max: undefined },
];

const STOCK_FILTERS = [
  { value: 'in_stock', label: 'Còn hàng' },
  { value: 'all', label: 'Tất cả tồn kho' },
];

function mapSort(value: string) {
  switch (value) {
    case 'price_asc': return 'price_asc';
    case 'price_desc': return 'price_desc';
    default: return undefined;
  }
}

export default function ProductListPage() {
  const [searchParams] = useSearchParams();

  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') ?? '');
  const [searchInput, setSearchInput] = useState(searchParams.get('q') ?? '');
  const [page, setPage] = useState(0);

  // Sync with searches submitted from the global header (?q=...).
  useEffect(() => {
    const q = searchParams.get('q') ?? '';
    setSearchQuery(q);
    setSearchInput(q);
    setPage(0);
  }, [searchParams]);
  const [sort, setSort] = useState('');
  const [priceRange, setPriceRange] = useState(0);
  const [stockFilter, setStockFilter] = useState<'in_stock' | 'all'>('in_stock');
  const [flashOnly, setFlashOnly] = useState(false);
  const [showFilters, setShowFilters] = useState(false);

  // ─── Search suggest (debounced) ────────────────────────────────────────
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggest, setShowSuggest] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(-1);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const suggestContainerRef = useRef<HTMLDivElement>(null);

  // Debounce: after user stops typing 500ms, fetch suggestions
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!searchInput || searchInput.trim().length === 0) {
      setSuggestions([]);
      setShowSuggest(false);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      try {
        const items = await productApi.getSuggestions(searchInput.trim(), 5);
        setSuggestions(items);
        setShowSuggest(items.length > 0);
        setHighlightIndex(-1);
      } catch {
        setSuggestions([]);
      }
    }, 500);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [searchInput]);

  // Close suggestions on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (suggestContainerRef.current && !suggestContainerRef.current.contains(e.target as Node)) {
        setShowSuggest(false);
      }
    };
    if (showSuggest) document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [showSuggest]);

  const selectSuggestion = (text: string) => {
    setSearchInput(text);
    setSearchQuery(text);
    setSuggestions([]);
    setShowSuggest(false);
    setPage(0);
  };

  const selectedPriceRange = PRICE_RANGES[priceRange];

  // Live flash-sale session powers the homepage teaser (backend uses "LIVE").
  const { data: flashSessions } = useQuery({
    queryKey: ['flash-sessions'],
    queryFn: () => flashSaleApi.getSessions().then(r => r.data.data.content),
    staleTime: 1000 * 60,
  });
  const liveSession = (flashSessions ?? []).find(
    s => (s.status as string) === 'LIVE' || s.status === 'ACTIVE',
  );

  const { data, isLoading, error } = useQuery({
    queryKey: ['products', page, searchQuery, sort, priceRange, stockFilter, flashOnly],
    queryFn: () =>
      productApi.getProducts({
        search: searchQuery || undefined,
        priceMin: selectedPriceRange.min,
        priceMax: selectedPriceRange.max,
        inStock: stockFilter === 'in_stock',
        isFlash: flashOnly ? true : undefined,
        page,
        size: 20,
        sort: mapSort(sort),
      }).then(r => r.data.data),
    staleTime: 1000 * 30,
  });

  const products: ProductDetail[] = (data as any)?.content ?? (Array.isArray(data) ? data : []);
  const totalPages = (data as any)?.totalPages ?? 1;

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchQuery(searchInput);
    setSuggestions([]); // dismiss dropdown
    setPage(0);
  };

  const activeFiltersCount = [
    sort !== '',
    priceRange !== 0,
    stockFilter !== 'in_stock',
    flashOnly,
  ].filter(Boolean).length;

  return (
    <div className="bg-gray-50 min-h-screen">
      {/* Hero banner */}
      <div className="bg-gradient-to-r from-blue-600 to-violet-700 text-white py-12 px-4">
        <div className="max-w-7xl mx-auto text-center">
          <p className="text-blue-200 text-sm font-medium uppercase tracking-widest mb-2">Khám phá ngay</p>
          <h1 className="text-4xl sm:text-5xl font-bold mb-4">Hàng ngàn sản phẩm</h1>
          <p className="text-blue-100 text-lg mb-8">Giá tốt nhất, giao hàng nhanh nhất toàn quốc</p>
          <form onSubmit={handleSearch} className="max-w-lg mx-auto">
            <div className="relative" ref={suggestContainerRef}>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={searchInput}
                  onChange={e => setSearchInput(e.target.value)}
                  onFocus={() => { if (suggestions.length > 0) setShowSuggest(true); }}
                  onKeyDown={e => {
                    if (!showSuggest || suggestions.length === 0) return;
                    if (e.key === 'ArrowDown') { e.preventDefault(); setHighlightIndex(i => Math.min(i + 1, suggestions.length - 1)); }
                    else if (e.key === 'ArrowUp') { e.preventDefault(); setHighlightIndex(i => Math.max(i - 1, -1)); }
                    else if (e.key === 'Enter') {
                      if (highlightIndex >= 0) { e.preventDefault(); selectSuggestion(suggestions[highlightIndex]); }
                    }
                    else if (e.key === 'Escape') { setShowSuggest(false); }
                  }}
                  placeholder="Tìm kiếm sản phẩm..."
                  className="flex-1 px-5 py-3 rounded-xl text-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                />
                <button
                  type="submit"
                  className="px-6 py-3 bg-white text-blue-600 font-semibold rounded-xl hover:bg-blue-50 transition-colors text-sm"
                >
                  Tìm kiếm
                </button>
              </div>
              {/* Suggest dropdown */}
              {showSuggest && suggestions.length > 0 && (
                <div className="absolute top-full mt-1 left-0 right-0 bg-white rounded-xl shadow-lg border border-gray-100 overflow-hidden z-50">
                  {suggestions.map((s, i) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => selectSuggestion(s)}
                      onMouseEnter={() => setHighlightIndex(i)}
                      className={`w-full text-left px-5 py-3 text-sm transition-colors ${
                        i === highlightIndex ? 'bg-blue-50 text-blue-700' : 'text-gray-700 hover:bg-gray-50'
                      }`}
                    >
                      <span className="inline-flex items-center gap-2">
                        <svg className="w-4 h-4 text-gray-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                        {s}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </form>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

        {/* Flash Sale teaser — only when a session is live */}
        {liveSession && (
          <Link
            to="/flash-sales"
            className="flex items-center gap-4 mb-5 p-4 rounded-2xl bg-gradient-to-r from-red-500 to-orange-500 text-white hover:shadow-lg transition-shadow"
          >
            <span className="text-3xl shrink-0">⚡</span>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg">FLASH SALE</span>
                <span className="px-2 py-0.5 rounded-full bg-white/25 text-[11px] font-bold uppercase tracking-wide">
                  Đang diễn ra
                </span>
              </div>
              <p className="text-sm text-white/90 truncate">{liveSession.name}</p>
            </div>
            <span className="shrink-0 px-4 py-2 rounded-xl bg-white text-red-600 font-semibold text-sm">
              Mua ngay →
            </span>
          </Link>
        )}

        {/* Filter toggle */}
        <div className="flex items-center justify-end mb-4">
          <button
            onClick={() => setShowFilters(f => !f)}
            className="flex items-center gap-1.5 px-3.5 py-1.5 bg-white border border-gray-200 rounded-xl text-sm font-medium text-gray-700 hover:border-gray-300 hover:text-gray-900 transition-colors shrink-0"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
            </svg>
            Bộ lọc
            {activeFiltersCount > 0 && (
              <span className="w-5 h-5 bg-blue-600 text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                {activeFiltersCount}
              </span>
            )}
          </button>
        </div>

        {/* Advanced filter panel */}
        {showFilters && (
          <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-4 space-y-5">
            {/* Sort */}
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Sắp xếp</p>
              <div className="flex flex-wrap gap-2">
                {SORT_OPTIONS.map(opt => (
                  <button
                    key={opt.value}
                    onClick={() => { setSort(opt.value); setPage(0); }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                      sort === opt.value
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-gray-50 text-gray-700 border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Price range */}
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Khoảng giá</p>
              <div className="flex flex-wrap gap-2">
                {PRICE_RANGES.map((range, i) => (
                  <button
                    key={i}
                    onClick={() => { setPriceRange(i); setPage(0); }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                      priceRange === i
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-gray-50 text-gray-700 border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    {range.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Stock + flash sale */}
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Tình trạng</p>
              <div className="flex flex-wrap gap-2">
                {STOCK_FILTERS.map(opt => (
                  <button
                    key={opt.value}
                    onClick={() => { setStockFilter(opt.value as 'in_stock' | 'all'); setPage(0); }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                      stockFilter === opt.value
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-gray-50 text-gray-700 border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
                <button
                  onClick={() => { setFlashOnly(v => !v); setPage(0); }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                    flashOnly
                      ? 'bg-red-600 text-white border-red-600'
                      : 'bg-gray-50 text-gray-700 border-gray-200 hover:border-red-300'
                  }`}
                >
                  Chỉ Flash Sale
                </button>
              </div>
            </div>

            {/* Active filter chips + clear */}
            {activeFiltersCount > 0 && (
              <div className="flex items-center gap-2 pt-2 border-t border-gray-100">
                <span className="text-xs text-gray-500">Đang lọc:</span>
                {sort && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-full">
                    {SORT_OPTIONS.find(s => s.value === sort)?.label}
                    <button onClick={() => { setSort(''); setPage(0); }} className="hover:text-blue-900 ml-0.5">×</button>
                  </span>
                )}
                {priceRange !== 0 && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-full">
                    {PRICE_RANGES[priceRange].label}
                    <button onClick={() => { setPriceRange(0); setPage(0); }} className="hover:text-blue-900 ml-0.5">×</button>
                  </span>
                )}
                {stockFilter !== 'in_stock' && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-full">
                    Tất cả tồn kho
                    <button onClick={() => { setStockFilter('in_stock'); setPage(0); }} className="hover:text-blue-900 ml-0.5">×</button>
                  </span>
                )}
                {flashOnly && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-red-50 text-red-700 text-xs rounded-full">
                    Flash Sale
                    <button onClick={() => { setFlashOnly(false); setPage(0); }} className="hover:text-red-900 ml-0.5">×</button>
                  </span>
                )}
                <button
                  onClick={() => { setSort(''); setPriceRange(0); setStockFilter('in_stock'); setFlashOnly(false); setPage(0); }}
                  className="text-xs text-red-500 hover:text-red-700 font-medium ml-1"
                >
                  Xóa tất cả
                </button>
              </div>
            )}
          </div>
        )}

        {/* Results header */}
        <div className="flex items-center justify-between mb-3">
          <p className="text-base font-semibold text-gray-900">
            {searchQuery ? `Kết quả tìm kiếm cho "${searchQuery}"` : 'Gợi ý hôm nay'}
            {(sort || priceRange !== 0 || stockFilter !== 'in_stock' || flashOnly) && (
              <span className="ml-1 text-sm font-normal text-gray-500">— đã lọc</span>
            )}
          </p>
          <p className="text-xs text-gray-400">
            {(data as any)?.totalElements > 0 && `${(data as any).totalElements} sản phẩm`}
          </p>
        </div>

        {/* Loading */}
        {isLoading && (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="bg-white rounded-2xl border border-gray-100 overflow-hidden animate-pulse">
                <div className="aspect-square bg-gray-200" />
                <div className="p-3 space-y-2">
                  <div className="h-4 bg-gray-200 rounded w-3/4" />
                  <div className="h-4 bg-gray-200 rounded w-1/2" />
                  <div className="h-6 bg-gray-200 rounded w-2/3" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Error */}
        {error && !isLoading && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-4">
            Không thể tải sản phẩm. Vui lòng thử lại.{' '}
            <button onClick={() => window.location.reload()} className="underline font-medium">Làm mới</button>
          </div>
        )}

        {/* Empty */}
        {!isLoading && !error && products.length === 0 && (
          <div className="text-center py-20">
            <span className="text-5xl block mb-4">🔍</span>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Không tìm thấy sản phẩm</h3>
            <p className="text-gray-500">Thử thay đổi bộ lọc hoặc từ khoá tìm kiếm.</p>
          </div>
        )}

        {/* Grid */}
        {!isLoading && !error && products.length > 0 && (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 mb-6">
              {products.map(p => (
                <ProductCard key={p.productId} product={p} />
              ))}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex justify-center gap-2">
                <button
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50"
                >
                  ← Trước
                </button>
                <span className="px-4 py-2 text-sm text-gray-600">
                  Trang {page + 1} / {totalPages}
                </span>
                <button
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50"
                >
                  Sau →
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
