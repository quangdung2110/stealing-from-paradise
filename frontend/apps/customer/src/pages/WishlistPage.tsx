import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useWishlistStore } from '@shared/store/wishlistStore';
import { fmtVnd } from '@shared/utils/format';
import { Skeleton, EmptyState } from '@shared/components/ui';

export default function WishlistPage() {
  const { items, isLoading, error, fetchWishlist, remove } = useWishlistStore();

  useEffect(() => {
    fetchWishlist();
  }, [fetchWishlist]);

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">❤️ Sản phẩm yêu thích</h1>

      {error && (
        <div className="mb-4 p-3 rounded-xl bg-red-50 text-red-600 text-sm">{error}</div>
      )}

      {isLoading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <Skeleton className="aspect-square rounded-none" />
              <div className="p-3 space-y-2">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-5 w-1/2" />
                <Skeleton className="h-9 w-full" />
              </div>
            </div>
          ))}
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          iconKey="heart"
          title="Chưa có sản phẩm yêu thích"
          description="Nhấn vào trái tim trên sản phẩm để lưu lại những món bạn thích."
          action={
            <Link
              to="/products"
              className="inline-flex items-center justify-center h-10 px-4 text-sm font-semibold rounded-xl text-white bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 shadow-sm transition-colors"
            >
              Khám phá sản phẩm
            </Link>
          }
        />
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {items.map((item) => (
            <div
              key={item.productId}
              className="bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-lg transition-all flex flex-col"
            >
              <Link to={`/products/${item.productId}`} className="block">
                <div className="relative bg-gradient-to-br from-gray-100 to-gray-200 aspect-square flex items-center justify-center overflow-hidden">
                  {item.thumbnailUrl ? (
                    <img src={item.thumbnailUrl} alt={item.productName} className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-4xl">🛍️</span>
                  )}
                  {item.available === false && (
                    <span className="absolute top-2 left-2 px-2 py-0.5 rounded-full text-xs font-bold text-white bg-gray-500">
                      Ngừng bán
                    </span>
                  )}
                </div>
              </Link>
              <div className="p-3 flex flex-col flex-1">
                <Link to={`/products/${item.productId}`} className="flex-1">
                  <h3 className="text-sm font-medium text-gray-900 line-clamp-2 mb-2 hover:text-blue-600 transition-colors">
                    {item.productName}
                  </h3>
                </Link>
                {item.minPrice != null && (
                  <p className="text-base font-bold text-red-600 mb-2">{fmtVnd(item.minPrice)}</p>
                )}
                <div className="flex gap-1.5 mt-auto">
                  <Link
                    to={`/products/${item.productId}`}
                    className="flex-1 py-2 border border-blue-600 text-blue-600 hover:bg-blue-50 text-xs font-semibold rounded-xl transition-colors text-center"
                  >
                    Xem
                  </Link>
                  <button
                    onClick={() => remove(item.productId)}
                    className="flex-1 py-2 border border-red-200 text-red-500 hover:bg-red-50 text-xs font-semibold rounded-xl transition-colors"
                  >
                    Bỏ thích
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
