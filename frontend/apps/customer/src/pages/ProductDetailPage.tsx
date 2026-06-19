import { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useCartStore } from '@shared/store/cartStore';
import WishlistButton from '@/components/WishlistButton';
import { productApi, type ProductDetail } from '@shared/api/product.api';
import { addressApi } from '@shared/api/address.api';
import { cartApi } from '@shared/api/cart.api';
import { userApi } from '@shared/api/user.api';
import { useAuthStore } from '@shared/store/authStore';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function formatAttributeValue(value: unknown): string {
  if (value == null) return '—';
  if (Array.isArray(value)) return value.map(v => formatAttributeValue(v)).join(', ');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export default function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>();
  const navigate = useNavigate();
  const { addToCart } = useCartStore();
  const [selectedVariantIndex, setSelectedVariantIndex] = useState<number | null>(null);
  const [selectedImage, setSelectedImage] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [isAdding, setIsAdding] = useState(false);
  const [isBuyNow, setIsBuyNow] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [addError, setAddError] = useState<string | null>(null);
  const navTimerRef = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => { if (navTimerRef.current) clearTimeout(navTimerRef.current); }, []);

  const { data: product, isLoading, error } = useQuery({
    queryKey: ['product', productId],
    queryFn: () => productApi.getProductById(productId!).then(r => r.data.data),
    enabled: !!productId,
    retry: 1,
  });

  const { data: sellerInfo } = useQuery({
    queryKey: ['seller-public', product?.sellerId],
    queryFn: () => userApi.getSellerPublic(product!.sellerId).then(r => r.data.data),
    enabled: !!product?.sellerId,
    staleTime: 1000 * 60 * 5,
    retry: 1,
  });

  // Fetch product count separately — identity-service returns productCount=0 (hardcoded)
  const { data: sellerProducts } = useQuery({
    queryKey: ['seller-product-count', product?.sellerId],
    queryFn: () =>
      productApi.getProducts({ sellerId: product!.sellerId, page: 0, size: 1 }).then(r => r.data.data),
    enabled: !!product?.sellerId,
    staleTime: 1000 * 30,
    retry: 1,
  });
  const sellerProductCount = (sellerProducts as any)?.totalElements ?? sellerInfo?.productCount;

  // Auto-select first in-stock variant when product loads
  useEffect(() => {
    if (product?.variants && product.variants.length > 0) {
      const firstInStock = product.variants.findIndex(v => v.stockQuantity > 0);
      setSelectedVariantIndex(firstInStock >= 0 ? firstInStock : 0);
    }
  }, [product]);

  const { isAuthenticated } = useAuthStore();

  const { data: addresses = [] } = useQuery({
    queryKey: ['addresses'],
    queryFn: async () => {
      const res = await addressApi.list();
      return res.data.data ?? [];
    },
    enabled: isAuthenticated,
    retry: 1,
  });

  const selectedVariant = selectedVariantIndex !== null ? product?.variants?.[selectedVariantIndex] : null;
  const hasVariants = product?.variants && product.variants.length > 0;
  const isFlash = selectedVariant?.isFlash ?? false;
  const price = selectedVariant?.price ?? 0;
  const originalPrice = selectedVariant?.originalPrice ?? price;
  const disc = price && originalPrice && originalPrice > price
    ? Math.round((1 - price / originalPrice) * 100)
    : null;
  const maxQty = selectedVariant?.stockQuantity ?? 0;
  const isOutOfStock = !selectedVariant || maxQty <= 0;

  const handleAddToCart = async () => {
    if (!product || !selectedVariant) {
      setAddError('Vui lòng chọn phân loại sản phẩm trước khi thêm vào giỏ hàng.');
      return;
    }
    setIsAdding(true);
    setAddError(null);
    setSuccessMsg(null);
    try {
      await addToCart(selectedVariant.skuCode, quantity);
      setSuccessMsg('Đã thêm vào giỏ hàng!');
      navTimerRef.current = setTimeout(() => navigate('/cart'), 1200);
    } catch (err: any) {
      setAddError(err?.response?.data?.message || err?.response?.data?.error || 'Không thể thêm vào giỏ hàng.');
    } finally {
      setIsAdding(false);
    }
  };

  const handleBuyNow = async () => {
    if (!product || !selectedVariant) {
      setAddError('Vui lòng chọn phân loại sản phẩm trước khi mua ngay.');
      return;
    }
    setIsBuyNow(true);
    setAddError(null);
    setSuccessMsg(null);
    try {
      const addr = (addresses as any[]).find((a: any) => a.isDefault) ?? (addresses as any[])[0];
      if (!addr) {
        setAddError('Vui lòng thêm địa chỉ giao hàng trước khi mua hàng.');
        setIsBuyNow(false);
        return;
      }

      if ((selectedVariant as any).stockQuantity <= 0) {
        setAddError('Sản phẩm đã hết hàng.');
        setIsBuyNow(false);
        return;
      }

      // Add item to cart, then find it in the updated cart by variantId/skuCode
      await cartApi.addItem(selectedVariant.skuCode, quantity, (product as any).fsItemId);
      const { data: cartRes } = await cartApi.getCart();
      const cartData = cartRes?.data;

      const newestItem = (cartData?.sellers ?? [])
        .flatMap(s => s?.items ?? [])
        .find(item => item?.variantId === selectedVariant.variantId || item?.skuCode === selectedVariant.skuCode);

      if (!newestItem?.cartItemId) {
        throw new Error('Không tìm thấy sản phẩm trong giỏ hàng');
      }

      // Go to the checkout review flow (preview → submit)
      navigate('/checkout', {
        state: { selectedItemIds: [newestItem.cartItemId] },
      });
    } catch (err: any) {
      setAddError(err?.response?.data?.message || err?.response?.data?.error || 'Mua ngay thất bại.');
      setIsBuyNow(false);
    }
  };

  if (!productId) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500">ID sản phẩm không hợp lệ.</p>
        <Link to="/products" className="text-blue-600 hover:underline mt-2 inline-block">← Quay lại</Link>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="bg-gray-50 min-h-screen py-8">
        <div className="max-w-5xl mx-auto px-4 sm:px-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 animate-pulse">
            <div className="bg-white rounded-2xl border p-8">
              <div className="aspect-square bg-gray-200 rounded-xl" />
            </div>
            <div className="space-y-4">
              <div className="h-8 bg-gray-200 rounded w-3/4" />
              <div className="h-4 bg-gray-200 rounded w-1/2" />
              <div className="h-12 bg-gray-200 rounded w-1/3" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500 mb-4">Không tìm thấy sản phẩm.</p>
        <Link to="/products" className="text-blue-600 hover:underline">← Quay lại danh sách</Link>
      </div>
    );
  }

  return (
    <div className="bg-gray-50 min-h-screen pt-8 pb-40 md:py-8">
      <div className="max-w-5xl mx-auto px-4 sm:px-6">
        {/* Breadcrumb */}
        <div className="mb-8 flex items-center gap-2 text-sm flex-wrap">
          <Link to="/products" className="text-blue-600 hover:underline">Sản phẩm</Link>
          <span className="text-gray-400">/</span>
          {product.categoryName && (
            <>
              <Link to={`/products?category=${product.categoryName}`} className="text-blue-600 hover:underline">
                {product.categoryName}
              </Link>
              <span className="text-gray-400">/</span>
            </>
          )}
          <span className="text-gray-600 line-clamp-1">{product.name}</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Images */}
          <div>
            <div className="bg-white rounded-2xl border border-gray-100 p-4 sm:p-6 mb-6 md:sticky md:top-6">
              <div className="aspect-square bg-gradient-to-br from-gray-100 to-gray-200 rounded-xl flex items-center justify-center text-8xl overflow-hidden">
                {product.images?.[selectedImage] ? (
                  <img src={product.images[selectedImage]} alt={product.name} className="w-full h-full object-contain" />
                ) : '🛍️'}
              </div>
              {product.images && product.images.length > 1 && (
                <div className="grid grid-cols-5 gap-2.5 mt-4">
                  {product.images.map((img, i) => (
                    <button
                      key={i}
                      onClick={() => setSelectedImage(i)}
                      className={`aspect-square bg-gray-100 rounded-lg overflow-hidden ring-2 transition-all ${
                        selectedImage === i ? 'ring-blue-500' : 'ring-transparent hover:ring-blue-300'
                      }`}
                    >
                      <img src={img} alt="" className="w-full h-full object-cover" />
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Product info */}
          <div>
            {/* Header */}
            <div className="mb-4">
              {isFlash && (
                <span className="inline-block px-3 py-1 bg-red-100 text-red-700 text-xs font-bold rounded-full mb-3">
                  ⚡ FLASH SALE
                </span>
              )}
              <h1 className="text-3xl font-bold text-gray-900 mb-3">{product.name}</h1>
            </div>

            {/* Price */}
            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <div className="flex items-baseline gap-3 mb-3">
                <span className="text-4xl font-bold text-red-600">{fmt(price)}</span>
                {disc && (
                  <>
                    <span className="text-xl text-gray-400 line-through">{fmt(originalPrice)}</span>
                    <span className="text-sm font-semibold text-green-600 bg-green-50 px-2 py-1 rounded-lg">
                      -{disc}%
                    </span>
                  </>
                )}
              </div>
              <div className="flex items-center gap-2 text-sm text-gray-600">
                {isOutOfStock ? (
                  <>
                    <span className="text-red-500 font-semibold">✗ Hết hàng</span>
                  </>
                ) : (
                  <>
                    <span className="text-green-600 font-semibold">✓ Còn hàng</span>
                    <span className="text-gray-300">·</span>
                    <span>{maxQty} sản phẩm</span>
                  </>
                )}
              </div>
            </div>

            {/* Seller Card */}
            {product.sellerId > 0 && (
              <Link
                to={`/sellers/${product.sellerId}`}
                className="bg-white rounded-2xl border border-gray-100 p-5 mb-6 flex items-center gap-4 hover:shadow-md hover:border-blue-200 transition-all group"
              >
                <div className="w-14 h-14 rounded-full bg-gradient-to-br from-blue-100 to-blue-200 flex items-center justify-center text-2xl shrink-0 overflow-hidden">
                  {sellerInfo?.avatarUrl ? (
                    <img src={sellerInfo.avatarUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    '🏪'
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-gray-900 group-hover:text-blue-600 transition-colors truncate">
                    {sellerInfo?.sellerName || product.sellerName || `Shop ${product.sellerId}`}
                  </p>
                  <div className="flex items-center gap-3 mt-0.5 text-xs text-gray-500">
                    {sellerInfo?.productCount != null && sellerProductCount > 0 && (
                      <span>{sellerProductCount} sản phẩm</span>
                    )}
                    {sellerInfo?.joinedAt && (
                      <span>Tham gia {new Date(sellerInfo.joinedAt).toLocaleDateString('vi-VN')}</span>
                    )}
                  </div>
                </div>
                <span className="text-sm font-medium text-blue-600 shrink-0 group-hover:translate-x-0.5 transition-transform">
                  Xem shop →
                </span>
              </Link>
            )}

            {/* Variants */}
            {hasVariants && (
              <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">Phân loại</h3>
                <div className="flex flex-wrap gap-2">
                  {product.variants!.map((v, i) => (
                    <button
                      key={v.skuCode}
                      onClick={() => {
                        setSelectedVariantIndex(i);
                        setQuantity(1);
                      }}
                      className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all ${
                        selectedVariantIndex === i
                          ? 'border-blue-500 bg-blue-50 text-blue-700'
                          : 'border-gray-200 text-gray-700 hover:border-blue-300'
                      } ${v.stockQuantity <= 0 ? 'opacity-40 cursor-not-allowed' : ''}`}
                      disabled={v.stockQuantity <= 0}
                    >
                      {v.variantName}
                      {v.stockQuantity <= 5 && v.stockQuantity > 0 && (
                        <span className="ml-1 text-xs text-orange-500">(còn {v.stockQuantity})</span>
                      )}
                      {v.stockQuantity <= 0 && <span className="ml-1 text-xs text-gray-400">(hết)</span>}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Quantity & Add */}
            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <div className="flex items-center gap-4 mb-4">
                <span className="text-sm font-medium text-gray-700">Số lượng:</span>
                <div className="flex items-center border border-gray-200 rounded-lg">
                  <button
                    onClick={() => setQuantity(q => Math.max(1, q - 1))}
                    className="w-10 h-10 flex items-center justify-center hover:bg-gray-100 font-bold text-lg"
                  >
                    −
                  </button>
                  <span className="w-14 text-center font-semibold text-lg">{quantity}</span>
                  <button
                    onClick={() => setQuantity(q => Math.min(maxQty, q + 1))}
                    disabled={maxQty <= 0}
                    className="w-10 h-10 flex items-center justify-center hover:bg-gray-100 font-bold text-lg disabled:opacity-30"
                  >
                    +
                  </button>
                </div>
                <span className="text-sm text-gray-500">
                  (tối đa {maxQty})
                </span>
              </div>

              <div className="flex items-stretch gap-3">
                <WishlistButton
                  productId={productId!}
                  className="!w-14 !h-auto self-stretch shrink-0 text-2xl border border-gray-200 !rounded-xl"
                />

                <button
                  onClick={handleAddToCart}
                  disabled={isAdding || isBuyNow || isOutOfStock || (hasVariants && !selectedVariant)}
                  className={`flex-1 py-4 px-3 border-2 font-bold text-base sm:text-lg rounded-xl transition-all ${
                    isOutOfStock || (hasVariants && !selectedVariant)
                      ? 'border-gray-300 text-gray-400 bg-gray-100 cursor-not-allowed'
                      : 'border-blue-600 text-blue-600 hover:bg-blue-50'
                  }`}
                >
                  {isAdding ? 'Đang thêm…' : '🛒 Thêm vào giỏ'}
                </button>

                <button
                  onClick={handleBuyNow}
                  disabled={isAdding || isBuyNow || isOutOfStock || (hasVariants && !selectedVariant)}
                  className={`flex-1 py-4 px-3 font-bold text-base sm:text-lg rounded-xl transition-all ${
                    isOutOfStock || (hasVariants && !selectedVariant)
                      ? 'bg-gray-400 text-white cursor-not-allowed'
                      : 'bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 text-white'
                  }`}
                >
                  {isBuyNow ? 'Đang xử lý…' : '⚡ Mua ngay'}
                </button>
              </div>

              {successMsg && (
                <div className="mt-3 p-3 bg-green-100 text-green-800 text-sm rounded-lg text-center font-semibold">
                  ✓ {successMsg}
                </div>
              )}
              {addError && (
                <div className="mt-3 p-3 bg-red-50 text-red-700 text-sm rounded-lg text-center">
                  {addError}
                </div>
              )}
            </div>

            {/* Badges */}
            <div className="flex flex-col gap-2 text-sm text-gray-600">
              <div className="flex items-center gap-2">
                <span>✓</span> Miễn phí giao hàng cho đơn từ 500K
              </div>
              <div className="flex items-center gap-2">
                <span>✓</span> Đổi trả trong 7 ngày
              </div>
              <div className="flex items-center gap-2">
                <span>✓</span> Hỗ trợ 24/7
              </div>
              {isFlash && (
                <div className="flex items-center gap-2">
                  <span>⚡</span> Sản phẩm flash sale — giá đặc biệt
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Attributes (key | value) — render only when product has any */}
        {product.attributes && Object.keys(product.attributes).length > 0 && (
          <div className="mt-12 bg-white rounded-2xl border border-gray-100 p-8">
            <h2 className="text-2xl font-bold text-gray-900 mb-4">Thông tin sản phẩm</h2>
            <dl className="grid grid-cols-1 sm:grid-cols-3 gap-y-3 gap-x-4 text-sm">
              {Object.entries(product.attributes).map(([key, value]) => (
                <div key={key} className="contents">
                  <dt className="col-span-1 text-gray-500 capitalize">{key}</dt>
                  <dd className="col-span-2 text-gray-900 font-medium break-words">
                    {formatAttributeValue(value)}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        )}

        {/* Description */}
        {product.description && (
          <div className="mt-12 bg-white rounded-2xl border border-gray-100 p-8">
            <h2 className="text-2xl font-bold text-gray-900 mb-4">Mô tả sản phẩm</h2>
            <p className="text-gray-700 leading-relaxed whitespace-pre-line">{product.description}</p>
          </div>
        )}
      </div>

      <div className="fixed inset-x-0 bottom-[calc(4rem+env(safe-area-inset-bottom))] z-40 border-t border-gray-200 bg-white/95 px-4 py-3 shadow-[0_-8px_24px_rgba(15,23,42,0.08)] backdrop-blur md:hidden">
        {(successMsg || addError) && (
          <p className={`mb-2 rounded-lg px-3 py-2 text-center text-xs font-semibold ${
            successMsg ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
          }`}>
            {successMsg || addError}
          </p>
        )}
        <div className="mx-auto flex max-w-5xl items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-xs text-gray-500">Tạm tính</p>
            <p className="truncate text-lg font-bold text-red-600">{fmt(price * quantity)}</p>
          </div>
          <button
            onClick={handleAddToCart}
            disabled={isAdding || isBuyNow || maxQty <= 0}
            className="rounded-xl border-2 border-blue-600 bg-white px-3 py-3 text-sm font-bold text-blue-600 transition-colors hover:bg-blue-50 disabled:border-gray-300 disabled:text-gray-400"
          >
            {isAdding ? 'Đang thêm...' : 'Thêm vào giỏ'}
          </button>
          <button
            onClick={handleBuyNow}
            disabled={isAdding || isBuyNow || maxQty <= 0}
            className="rounded-xl bg-blue-600 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-blue-700 disabled:bg-gray-400"
          >
            {isBuyNow ? 'Đang xử lý...' : 'Mua ngay'}
          </button>
        </div>
      </div>
    </div>
  );
}
