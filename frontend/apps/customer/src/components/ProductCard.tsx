import { Link } from 'react-router-dom';
import { fmtVnd } from '@shared/utils/format';
import type { ProductDetail } from '@shared/api/product.api';
import WishlistButton from './WishlistButton';

function discountPct(original?: number, price?: number) {
  if (!original || !price || original <= price) return null;
  return Math.round((1 - price / original) * 100);
}

interface ProductCardProps {
  product: ProductDetail;
}

export default function ProductCard({ product }: ProductCardProps) {
  const price = product.price ?? 0;
  const original = product.originalPrice ?? price;
  const disc = discountPct(original, price);
  const img = product.images?.[0];

  return (
    <Link
      to={`/products/${product.productId}`}
      className="bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 group flex flex-col"
    >
      <div className="relative bg-gradient-to-br from-gray-100 to-gray-200 aspect-square flex items-center justify-center overflow-hidden">
        {img ? (
          <img src={img} alt={product.name} className="w-full h-full object-cover" />
        ) : (
          <span className="text-4xl">🛍️</span>
        )}
        {product.isFlash && (
          <span className="absolute top-2 left-2 px-2 py-0.5 rounded-full text-xs font-bold text-white bg-red-500">
            ⚡ Flash Sale
          </span>
        )}
        {disc && (
          <span className="absolute top-2 right-2 bg-white/90 text-green-700 font-bold text-xs px-2 py-0.5 rounded-full">
            -{disc}%
          </span>
        )}
        <WishlistButton productId={product.productId} className="absolute bottom-2 right-2" />
      </div>
      <div className="p-3 flex flex-col flex-1">
        <p className="text-xs text-gray-400 mb-1">{product.categoryName || product.categoryId || 'Sản phẩm'}</p>
        <h3 className="text-sm font-medium text-gray-900 line-clamp-2 mb-2 group-hover:text-blue-600 transition-colors">
          {product.name}
        </h3>
        <div className="flex items-baseline gap-1.5 mb-1">
          <span className="text-base font-bold text-red-600">{fmtVnd(price)}</span>
          {disc && (
            <span className="text-xs text-gray-400 line-through">{fmtVnd(original)}</span>
          )}
        </div>
        {product.rating != null && (
          <div className="flex items-center gap-1 mb-2">
            <span className="text-yellow-400 text-xs">{'★'.repeat(Math.round(product.rating))}</span>
            <span className="text-xs text-gray-400">({product.reviewsCount ?? 0})</span>
          </div>
        )}
        {product.stockAvailable > 0 && (
          <span className="mt-auto w-full py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-xl transition-colors text-center block">
            Xem chi tiết
          </span>
        )}
        {product.stockAvailable <= 0 && (
          <span className="mt-auto w-full py-2 bg-gray-300 text-white text-xs font-semibold rounded-xl text-center block">
            Hết hàng
          </span>
        )}
      </div>
    </Link>
  );
}
