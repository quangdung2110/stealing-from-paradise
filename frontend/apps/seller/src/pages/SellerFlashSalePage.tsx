import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@shared/lib/axios';
import { flashSaleApi, type FlashSaleItem } from '@shared/api/flashSale.api';
import { sellerApi, type SellerProduct, type SellerVariant } from '@shared/api/seller.api';
import type { ApiResponse, PageResponse } from '@shared/types/api';
import { Button, Skeleton, Badge, EmptyState } from '@shared/components/ui';
import { fmtDateTime, fmtVnd } from '@shared/utils/format';
import { notify } from '@shared/lib/toast';

/* ─── helpers ─────────────────────────────────────────────── */

function statusLabel(status: string) {
  switch (status) {
    case 'APPROVED':  return 'Đã duyệt';
    case 'REJECTED':  return 'Từ chối';
    case 'PENDING':   return 'Chờ duyệt';
    case 'SOLD_OUT':  return 'Hết hàng';
    default:          return status;
  }
}

function badgeTone(status: string) {
  switch (status) {
    case 'APPROVED':  return 'success' as const;
    case 'REJECTED':  return 'danger' as const;
    case 'PENDING':   return 'warning' as const;
    default:          return 'neutral' as const;
  }
}

function shortTime(iso?: string) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

function shortDate(iso?: string) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
}

/** Active statuses a seller can register for flash sale */
const REGISTRABLE_STATUSES = ['ACTIVE', 'PUBLISHED'];

function mapSellerProduct(p: any): SellerProduct {
  return {
    ...p,
    productId: p.productId ?? p.id,
    price: p.price ?? 0,
    stockAvailable: p.stockAvailable ?? p.totalStock ?? 0,
    variantsCount: p.variantsCount ?? p.variantCount ?? 0,
    images: p.images ?? (p.thumbnailUrl ? [p.thumbnailUrl] : []),
  };
}

/* ─── component ────────────────────────────────────────────── */

export default function SellerFlashSalePage() {
  const queryClient = useQueryClient();

  // ── selection state ──
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [selectedSku, setSelectedSku] = useState('');
  const [flashPrice, setFlashPrice] = useState('');
  const [flashStock, setFlashStock] = useState('');
  const [limitPerUser, setLimitPerUser] = useState('1');
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  // ── queries ──

  const sessionsQuery = useQuery({
    queryKey: ['seller-flash-sale-sessions'],
    queryFn: () => flashSaleApi.getSessions().then(r => r.data.data?.content ?? []),
    staleTime: 30_000,
  });
  const sessions = sessionsQuery.data ?? [];
  const upcomingSessions = sessions.filter(s => s.status === 'UPCOMING');

  // auto-select first session
  useEffect(() => {
    if (!selectedSessionId && upcomingSessions[0]) {
      setSelectedSessionId(upcomingSessions[0].id);
      setSelectedProductId('');
      setSelectedSku('');
    }
  }, [selectedSessionId, upcomingSessions]);

  const selectedSession = useMemo(
    () => sessions.find(s => s.id === selectedSessionId),
    [sessions, selectedSessionId],
  );

  const productsQuery = useQuery({
    queryKey: ['seller-flash-sale-products'],
    queryFn: () =>
      apiClient.get<ApiResponse<PageResponse<SellerProduct>>>('/sellers/me/products', {
        params: { page: 0, size: 200 },
      }).then(r => (r.data.data?.content ?? []).map(mapSellerProduct)),
    staleTime: 30_000,
  });
  const allProducts: SellerProduct[] = productsQuery.data ?? [];

  // Only show products that are registerable (ACTIVE / PUBLISHED)
  const activeProducts = useMemo(
    () => allProducts.filter(p => REGISTRABLE_STATUSES.includes(p.status)),
    [allProducts],
  );

  const filteredProducts = useMemo(
    () => activeProducts.filter(p =>
      !searchQuery || p.name.toLowerCase().includes(searchQuery.toLowerCase()),
    ),
    [activeProducts, searchQuery],
  );

  const variantsQuery = useQuery({
    queryKey: ['seller-flash-sale-variants', selectedProductId],
    queryFn: () => sellerApi.getVariants(selectedProductId).then(r => r.data.data ?? []),
    enabled: !!selectedProductId,
  });
  const variants: SellerVariant[] = variantsQuery.data ?? [];

  const selectedProduct = useMemo(
    () => activeProducts.find(p => p.productId === selectedProductId),
    [activeProducts, selectedProductId],
  );

  const selectedVariant = useMemo<SellerVariant | undefined>(
    () => variants.find(v => v.skuCode === selectedSku),
    [variants, selectedSku],
  );

  // When product changes, auto-select first variant + set defaults
  useEffect(() => {
    const first = variants[0];
    if (first) {
      setSelectedSku(first.skuCode);
      setFlashPrice(String(Math.max(1, Math.floor(first.price * 0.8))));
      setFlashStock(String(Math.min(first.stock, 10)));
      setLimitPerUser('1');
      setError(null);
    }
  }, [variants]); // eslint-disable-line react-hooks/exhaustive-deps

  const detailQuery = useQuery({
    queryKey: ['seller-flash-sale-detail', selectedSessionId],
    queryFn: () => flashSaleApi.getSession(selectedSessionId!).then(r => r.data.data),
    enabled: !!selectedSessionId,
    staleTime: 15_000,
  });
  const sessionItems: FlashSaleItem[] = detailQuery.data?.items ?? [];
  const myItems = useMemo(
    () => sessionItems,
    [sessionItems],
  );

  // ── mutations ──

  const registerMut = useMutation({
    mutationFn: () => {
      if (!selectedSessionId || !selectedSku) throw new Error('MISSING_SELECTION');
      return flashSaleApi.registerItem(selectedSessionId, {
        skuCode: selectedSku,
        flashPrice: Number(flashPrice),
        flashStock: Number(flashStock),
        limitPerUser: Number(limitPerUser) || 1,
      });
    },
    onSuccess: () => {
      setError(null);
      notify.success('Đã gửi sản phẩm vào phiên Flash Sale');
      queryClient.invalidateQueries({ queryKey: ['seller-flash-sale-detail', selectedSessionId] });
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Không thể đăng ký sản phẩm Flash Sale.');
    },
  });

  // ── derived state ──

  const submitDisabled =
    !selectedSessionId || !selectedSku || !selectedProductId ||
    Number(flashPrice) <= 0 ||
    Number(flashStock) <= 0 ||
    registerMut.isPending;

  const canRegister = upcomingSessions.length > 0 && activeProducts.length > 0;

  // ── render ──

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      {/* ── header ── */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">🚀 Đăng ký Flash Sale</h1>
        <p className="text-sm text-gray-500 mt-1">
          Chọn khung giờ Flash Sale, chọn sản phẩm đang bán và gửi admin duyệt.
        </p>
      </div>

      {!canRegister && !sessionsQuery.isLoading && !productsQuery.isLoading && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 text-amber-800 text-sm mb-6">
          {upcomingSessions.length === 0 && productsQuery.isFetched
            ? 'Hiện chưa có phiên Flash Sale nào đang nhận đăng ký. Vui lòng quay lại sau.'
            : activeProducts.length === 0
              ? 'Bạn chưa có sản phẩm ACTIVE nào để đăng ký Flash Sale. Vui lòng tạo và kích hoạt sản phẩm trước.'
              : ''}
        </div>
      )}

      {/* ── Step 1: Chọn khung giờ ── */}
      <section className="mb-6">
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-3">
          1. Chọn khung giờ Flash Sale
        </h2>

        {sessionsQuery.isLoading ? (
          <div className="flex gap-3">
            {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-20 w-48 rounded-xl" />)}
          </div>
        ) : upcomingSessions.length === 0 ? (
          <div className="bg-white border border-dashed border-gray-300 rounded-xl p-6 text-center text-sm text-gray-400">
            Không có phiên Flash Sale nào đang nhận đăng ký.
          </div>
        ) : (
          <div className="flex flex-wrap gap-3">
            {upcomingSessions.map(s => {
              const isSelected = s.id === selectedSessionId;
              return (
                <button
                  key={s.id}
                  onClick={() => {
                    setSelectedSessionId(s.id);
                    setSelectedProductId('');
                    setSelectedSku('');
                  }}
                  className={`
                    relative flex flex-col items-start p-4 rounded-xl border-2 text-left transition-all
                    ${isSelected
                      ? 'border-violet-500 bg-violet-50 shadow-sm'
                      : 'border-gray-200 bg-white hover:border-violet-300 hover:bg-violet-50/40'
                    }
                  `}
                >
                  {isSelected && (
                    <span className="absolute -top-2 -right-2 w-5 h-5 bg-violet-600 text-white rounded-full text-xs flex items-center justify-center font-bold">
                      ✓
                    </span>
                  )}
                  <span className="text-sm font-bold text-gray-900">{s.name}</span>
                  <div className="flex items-center gap-2 mt-1.5 text-xs text-gray-500">
                    <span className="inline-flex items-center gap-1">
                      <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                      {shortTime(s.startTime)}
                    </span>
                    <span className="text-gray-300">→</span>
                    <span>{shortTime(s.endTime)}</span>
                  </div>
                  <span className="text-xs text-gray-400 mt-0.5">{shortDate(s.startTime)}</span>
                </button>
              );
            })}
          </div>
        )}
      </section>

      {/* ── Step 2: Layout 2 cột (sản phẩm + form) ── */}
      <section>
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-3">
          2. Chọn sản phẩm & đăng ký
        </h2>

        {!selectedSessionId ? (
          <div className="bg-white border border-dashed border-gray-300 rounded-xl p-10 text-center text-sm text-gray-400">
            Vui lòng chọn khung giờ Flash Sale trước.
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
            {/* ── LEFT: product grid ── */}
            <div className="lg:col-span-3 bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <div className="p-4 border-b border-gray-100 flex items-center justify-between">
                <h3 className="font-semibold text-gray-900 text-sm">
                  Sản phẩm đang bán
                  <span className="ml-1.5 text-gray-400 font-normal">
                    ({filteredProducts.length})
                  </span>
                </h3>
                <input
                  type="text"
                  placeholder="Tìm sản phẩm…"
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  className="w-48 px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-violet-400"
                />
              </div>

              {productsQuery.isLoading ? (
                <div className="p-4 grid grid-cols-2 sm:grid-cols-3 gap-3">
                  {Array.from({ length: 6 }).map((_, i) => (
                    <div key={i} className="space-y-2">
                      <Skeleton className="h-28 w-full rounded-xl" />
                      <Skeleton className="h-4 w-3/4" />
                      <Skeleton className="h-3 w-1/2" />
                    </div>
                  ))}
                </div>
              ) : filteredProducts.length === 0 ? (
                <EmptyState
                  iconKey="package"
                  title={searchQuery ? 'Không tìm thấy sản phẩm' : 'Chưa có sản phẩm'}
                  description={searchQuery ? 'Thử từ khóa khác.' : 'Tạo sản phẩm và kích hoạt trước khi đăng ký.'}
                  className="py-10"
                />
              ) : (
                <div className="p-4 grid grid-cols-2 sm:grid-cols-3 gap-3 max-h-[520px] overflow-y-auto">
                  {filteredProducts.map(p => {
                    const isSelected = p.productId === selectedProductId;
                    const thumb = p.images?.[0];
                    return (
                      <button
                        key={p.productId}
                        onClick={() => {
                          setSelectedProductId(p.productId);
                          setSelectedSku('');
                        }}
                        className={`
                          relative flex flex-col items-start rounded-xl border-2 text-left transition-all
                          ${isSelected
                            ? 'border-violet-500 bg-violet-50/40 shadow-sm'
                            : 'border-gray-100 bg-white hover:border-violet-200 hover:shadow-sm'
                          }
                        `}
                      >
                        {/* image */}
                        <div className="w-full aspect-[4/3] bg-gray-50 rounded-t-xl overflow-hidden">
                          {thumb ? (
                            <img src={thumb} alt={p.name} className="w-full h-full object-cover" />
                          ) : (
                            <div className="w-full h-full flex items-center justify-center text-gray-300 text-3xl">
                              📦
                            </div>
                          )}
                        </div>
                        {/* info */}
                        <div className="p-2.5 w-full">
                          <p className="text-xs font-semibold text-gray-900 truncate w-full">{p.name}</p>
                          <div className="flex items-center justify-between mt-1">
                            <span className="text-xs font-bold text-violet-700">{fmtVnd(p.price)}</span>
                            <span className="text-[10px] text-gray-400">{p.stockAvailable} tồn</span>
                          </div>
                        </div>
                        {/* selected indicator */}
                        {isSelected && (
                          <span className="absolute top-2 right-2 w-5 h-5 bg-violet-600 text-white rounded-full text-xs flex items-center justify-center shadow">
                            ✓
                          </span>
                        )}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* ── RIGHT: registration form ── */}
            <div className="lg:col-span-2 space-y-4">
              {/* ── form card ── */}
              <div className="bg-white rounded-2xl border border-gray-100 p-5">
                <h3 className="font-semibold text-gray-900 text-sm mb-1">Thông tin đăng ký</h3>
                <p className="text-xs text-gray-400 mb-4">
                  Phiên: {selectedSession?.name} · {fmtDateTime(selectedSession?.startTime)}
                </p>

                {error && (
                  <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-xs mb-4">
                    {error}
                  </div>
                )}

                {!selectedProductId ? (
                  <div className="py-8 text-center text-xs text-gray-400">
                    👈 Chọn một sản phẩm từ danh sách bên trái
                  </div>
                ) : variantsQuery.isLoading ? (
                  <div className="space-y-3">
                    <Skeleton className="h-8 w-full rounded-lg" />
                    <Skeleton className="h-8 w-full rounded-lg" />
                    <Skeleton className="h-8 w-full rounded-lg" />
                  </div>
                ) : variants.length === 0 ? (
                  <div className="py-6 text-center text-xs text-gray-400">
                    Sản phẩm này chưa có biến thể.
                  </div>
                ) : (
                  <div className="space-y-3.5">
                    {/* selected product info */}
                    <div className="flex items-center gap-3 pb-3 border-b border-gray-100">
                      <div className="w-12 h-12 rounded-lg bg-gray-50 flex items-center justify-center overflow-hidden flex-shrink-0">
                        {selectedProduct?.images?.[0] ? (
                          <img src={selectedProduct.images[0]} alt="" className="w-full h-full object-cover" />
                        ) : (
                          <span className="text-xl">📦</span>
                        )}
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-gray-900 truncate">{selectedProduct?.name}</p>
                        <p className="text-xs text-gray-400">
                          Giá gốc: {selectedVariant ? fmtVnd(selectedVariant.price) : '—'} · Tồn: {selectedVariant ? selectedVariant.stock : '—'}
                        </p>
                      </div>
                    </div>

                    {/* variant select */}
                    <label className="block">
                      <span className="block text-xs font-medium text-gray-600 mb-1">Biến thể</span>
                      <select
                        value={selectedSku}
                        onChange={e => {
                          setSelectedSku(e.target.value);
                          const v = variants.find(x => x.skuCode === e.target.value);
                          if (v) {
                            setFlashPrice(String(Math.max(1, Math.floor(v.price * 0.8))));
                            setFlashStock(String(Math.min(v.stock, 10)));
                          }
                        }}
                        className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
                      >
                        {variants.map(v => (
                          <option key={v.skuCode} value={v.skuCode}>
                            {v.variantName || v.skuCode} — {fmtVnd(v.price)} (còn {v.stock})
                          </option>
                        ))}
                      </select>
                    </label>

                    {/* flash price + stock */}
                    <div className="grid grid-cols-2 gap-3">
                      <label className="block">
                        <span className="block text-xs font-medium text-gray-600 mb-1">Giá flash</span>
                        <div className="relative">
                          <input
                            type="number"
                            min="1"
                            value={flashPrice}
                            onChange={e => setFlashPrice(e.target.value)}
                            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
                          />
                          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400">₫</span>
                        </div>
                      </label>
                      <label className="block">
                        <span className="block text-xs font-medium text-gray-600 mb-1">Số lượng / Goal</span>
                        <input
                          type="number"
                          min="1"
                          max={selectedVariant?.stock ?? undefined}
                          value={flashStock}
                          onChange={e => setFlashStock(e.target.value)}
                          className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
                        />
                      </label>
                    </div>

                    {/* limit per user */}
                    <label className="block">
                      <span className="block text-xs font-medium text-gray-600 mb-1">Giới hạn mỗi khách</span>
                      <input
                        type="number"
                        min="1"
                        value={limitPerUser}
                        onChange={e => setLimitPerUser(e.target.value)}
                        className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
                      />
                    </label>

                    <Button
                      fullWidth
                      onClick={() => registerMut.mutate()}
                      disabled={submitDisabled}
                      loading={registerMut.isPending}
                    >
                      Gửi admin duyệt
                    </Button>
                  </div>
                )}
              </div>

              {/* ── items in session ── */}
              <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
                <div className="p-4 border-b border-gray-100">
                  <h3 className="font-semibold text-gray-900 text-sm">
                    ✅ Sản phẩm đã đăng ký
                    <span className="ml-1.5 text-gray-400 font-normal">({myItems.length})</span>
                  </h3>
                </div>

                {detailQuery.isLoading ? (
                  <div className="p-4 space-y-2">
                    {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10 w-full rounded-lg" />)}
                  </div>
                ) : myItems.length === 0 ? (
                  <div className="p-6 text-center text-xs text-gray-400">
                    Chưa có sản phẩm nào trong phiên này.
                  </div>
                ) : (
                  <div className="divide-y divide-gray-50 max-h-64 overflow-y-auto">
                    {myItems.map(item => (
                      <div key={item.id} className="flex items-center gap-3 px-4 py-3">
                        {item.imageUrl ? (
                          <img src={item.imageUrl} alt="" className="w-10 h-10 rounded-lg object-cover flex-shrink-0" />
                        ) : (
                          <div className="w-10 h-10 rounded-lg bg-gray-100 flex items-center justify-center text-lg flex-shrink-0">📦</div>
                        )}
                        <div className="min-w-0 flex-1">
                          <p className="text-sm font-medium text-gray-900 truncate">{item.productName || item.skuCode}</p>
                          <div className="flex items-center gap-2 text-xs text-gray-400">
                            <span className="text-red-600 font-semibold">{fmtVnd(item.flashPrice)}</span>
                            {item.originalPrice && (
                              <span className="line-through">{fmtVnd(item.originalPrice)}</span>
                            )}
                            <span>· SL: {item.flashStock}</span>
                          </div>
                        </div>
                        <Badge tone={badgeTone(item.status)}>
                          {statusLabel(item.status)}
                        </Badge>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
