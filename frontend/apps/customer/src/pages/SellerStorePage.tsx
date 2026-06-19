import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { productApi } from '@shared/api/product.api';
import { userApi } from '@shared/api/user.api';
import ProductCard from '@/components/ProductCard';
import { PageLoader } from '@shared/components/ui';

const SORT_OPTIONS = [
  { value: '', label: 'Mặc định' },
  { value: 'price_asc', label: 'Giá: Thấp → Cao' },
  { value: 'price_desc', label: 'Giá: Cao → Thấp' },
];

export default function SellerStorePage() {
  const { sellerId } = useParams<{ sellerId: string }>();
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('');

  const sellerIdNum = sellerId ? Number(sellerId) : 0;

  const { data: seller, isLoading: sellerLoading, error: sellerError } = useQuery({
    queryKey: ['seller-public', sellerIdNum],
    queryFn: () => userApi.getSellerPublic(sellerIdNum).then(r => r.data.data),
    enabled: sellerIdNum > 0,
    retry: 1,
  });

  const { data, isLoading: productsLoading, error: productsError } = useQuery({
    queryKey: ['seller-products', sellerIdNum, page, sort],
    queryFn: () =>
      productApi.getProducts({
        sellerId: sellerIdNum,
        page,
        size: 20,
        sort: sort || undefined,
      }).then(r => r.data.data),
    enabled: sellerIdNum > 0,
    staleTime: 1000 * 30,
  });

  const products: any[] = (data as any)?.content ?? (Array.isArray(data) ? data : []);
  const totalPages = (data as any)?.totalPages ?? 1;
  const totalElements = (data as any)?.totalElements;

  if (!sellerId || sellerIdNum <= 0) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500">ID người bán không hợp lệ.</p>
        <Link to="/products" className="text-blue-600 hover:underline mt-2 inline-block">← Quay lại</Link>
      </div>
    );
  }

  if (sellerLoading) {
    return <PageLoader />;
  }

  if (sellerError || !seller) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-20 text-center">
        <span className="text-5xl block mb-4">🏪</span>
        <p className="text-red-500 mb-4">Không tìm thấy shop này.</p>
        <Link to="/products" className="text-blue-600 hover:underline">← Quay lại danh sách</Link>
      </div>
    );
  }

  return (
    <div className="bg-gray-50 min-h-screen">
      {/* Seller Header */}
      <div className="bg-white border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="flex items-center gap-6">
            <div className="w-20 h-20 rounded-full bg-gradient-to-br from-blue-100 to-blue-200 flex items-center justify-center text-4xl shrink-0 overflow-hidden border-2 border-blue-100">
              {seller.avatarUrl ? (
                <img src={seller.avatarUrl} alt="" className="w-full h-full object-cover" />
              ) : (
                '🏪'
              )}
            </div>
            <div className="flex-1 min-w-0">
              <h1 className="text-2xl font-bold text-gray-900 mb-1">{seller.sellerName}</h1>
              <div className="flex flex-wrap items-center gap-4 text-sm text-gray-500">
                <span className="inline-flex items-center gap-1">
                  <span className="text-base">📦</span>
                  <span className="font-medium text-gray-700">{totalElements ?? '—'}</span> sản phẩm
                </span>
                <span className="text-gray-300">·</span>
                <span className="inline-flex items-center gap-1">
                  <span className="text-base">📅</span>
                  Tham gia {new Date(seller.joinedAt).toLocaleDateString('vi-VN', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                  })}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Products section */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {/* Sort tools */}
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-900">Sản phẩm của shop</h2>
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500 hidden sm:inline">Sắp xếp:</span>
            <div className="flex gap-1">
              {SORT_OPTIONS.map(opt => (
                <button
                  key={opt.value}
                  onClick={() => { setSort(opt.value); setPage(0); }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                    sort === opt.value
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'bg-white text-gray-700 border-gray-200 hover:border-blue-300'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Loading */}
        {productsLoading && (
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
        {productsError && !productsLoading && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
            Không thể tải sản phẩm. Vui lòng thử lại.{' '}
            <button onClick={() => window.location.reload()} className="underline font-medium">Làm mới</button>
          </div>
        )}

        {/* Empty */}
        {!productsLoading && !productsError && products.length === 0 && (
          <div className="text-center py-20">
            <span className="text-5xl block mb-4">📭</span>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Shop chưa có sản phẩm nào</h3>
            <p className="text-gray-500">Quay lại sau để xem thêm nhé!</p>
          </div>
        )}

        {/* Grid */}
        {!productsLoading && !productsError && products.length > 0 && (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 mb-6">
              {products.map(p => (
                <ProductCard key={p.productId} product={p} />
              ))}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex justify-center gap-2 pb-8">
                <button
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50 bg-white"
                >
                  ← Trước
                </button>
                <span className="px-4 py-2 text-sm text-gray-600">
                  Trang {page + 1} / {totalPages}
                </span>
                <button
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50 bg-white"
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
