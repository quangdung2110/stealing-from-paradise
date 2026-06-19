import { useState, useMemo, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useCartStore } from '@shared/store/cartStore';
import type { CartChangeDetail } from '@shared/api/cart.api';
import type { CartItem } from '@shared/api/cart.api';
import { Skeleton } from '@shared/components/ui';
import CheckoutStepper from '@/components/CheckoutStepper';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';
const itemKey = (item: CartItem) => item.cartItemId;

function isFlashExpired(iso?: string | null) {
  if (!iso) return false;
  return new Date(iso).getTime() < Date.now();
}

function CartChangeBanner({
  details,
  onRefresh,
  isLoading,
}: {
  details: CartChangeDetail[];
  onRefresh: () => void;
  isLoading: boolean;
}) {
  return (
    <div className="bg-orange-100 border-2 border-orange-400 rounded-2xl p-6 mb-8 shadow-lg">
      <div className="flex items-start gap-4">
        <div className="text-4xl shrink-0 mt-1">🚨</div>
        <div className="flex-1 min-w-0">
          <h3 className="text-xl font-bold text-orange-900 mb-2">
            Giỏ hàng đã thay đổi!
          </h3>
          <p className="text-base text-orange-800 mb-4">
            Một số sản phẩm trong giỏ hàng của bạn đã có sự thay đổi về giá.
            Vui lòng nhấn nút bên dưới để tải lại thông tin mới nhất trước khi thanh toán.
          </p>
          <div className="space-y-3">
            {details.map((d, i) => (
              <div key={i} className="bg-white/90 rounded-xl p-4 text-sm border border-orange-200">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-semibold text-gray-900 truncate min-w-0">
                    {d.productName || d.skuCode || d.variantId}
                  </p>
                  <span className="shrink-0 rounded-full bg-orange-100 px-3 py-1 text-xs font-semibold text-orange-700">
                    Giá đã thay đổi
                  </span>
                </div>
                {d.reason === 'PRICE_CHANGED' && (
                  <div className="flex items-center gap-3 mt-2 text-sm">
                    <span className="text-gray-500 line-through">{d.expectedValue}</span>
                    <svg className="w-4 h-4 text-orange-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
                    </svg>
                    <span className="font-bold text-orange-700 text-base">{d.currentValue}</span>
                  </div>
                )}
              </div>
            ))}
          </div>
          <button
            onClick={onRefresh}
            disabled={isLoading}
            className="mt-5 w-full sm:w-auto flex items-center justify-center gap-3 px-8 py-3.5 bg-orange-500 hover:bg-orange-600 text-white font-bold text-base rounded-xl transition-colors shadow-md hover:shadow-lg disabled:opacity-50"
          >
            {isLoading ? (
              <>
                <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Đang cập nhật...
              </>
            ) : (
              <>
                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                Cập nhật giỏ hàng ngay
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function CartPage() {
  const { cart, isLoading, fetchCart, updateQuantity, removeFromCart } = useCartStore();
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());

  const [quantityError, setQuantityError] = useState<string | null>(null);

  const getMaxQty = (item: CartItem) => {
    return item.stockAvailable;
  };

  const handleIncrease = (item: CartItem) => {
    const vid = item.variantId ?? String(item.cartItemId);
    const max = getMaxQty(item);
    if (item.quantity >= max) {
      setQuantityError(vid);
      setTimeout(() => setQuantityError(null), 2000);
      return;
    }
    updateQuantity(vid, item.quantity + 1);
  };

  const getTotal = () => {
    if (!selectedItems.size || !cart?.sellers) return 0;
    let total = 0;
    cart.sellers.forEach(seller => {
      seller.items.forEach(item => {
        const key = itemKey(item);
        if (selectedItems.has(key)) {
          const price = item.isFlash && item.flashPrice ? item.flashPrice : item.unitPrice;
          total += price * item.quantity;
        }
      });
    });
    return total;
  };

  const getSelectedSellerTotals = () => {
    if (!selectedItems.size || !cart?.sellers) return [];
    return cart.sellers
      .map(seller => {
        const sellerTotal = seller.items.reduce((sum, item) => {
          const key = itemKey(item);
          if (selectedItems.has(key)) {
            const price = item.isFlash && item.flashPrice ? item.flashPrice : item.unitPrice;
            return sum + price * item.quantity;
          }
          return sum;
        }, 0);
        return { sellerName: seller.sellerName, total: sellerTotal };
      })
      .filter(s => s.total > 0);
  };

  const toggleItemSelection = (itemKey: string) => {
    const newSelected = new Set(selectedItems);
    if (newSelected.has(itemKey)) {
      newSelected.delete(itemKey);
    } else {
      newSelected.add(itemKey);
    }
    setSelectedItems(newSelected);
  };

  const selectAllItems = () => {
    if (!cart?.sellers) return;
    if (selectedItems.size === getItemCount()) {
      setSelectedItems(new Set());
    } else {
      const all = new Set<string>();
      cart.sellers.forEach(seller => {
        seller.items.forEach(item => {
          all.add(itemKey(item));
        });
      });
      setSelectedItems(all);
    }
  };

  const getItemCount = () => {
    if (!cart?.sellers) return 0;
    return cart.sellers.reduce((sum, seller) => sum + seller.items.length, 0);
  };

  // ─── Price-change detection ────────────────────────────────────────────────
  // Backend updates priceSnapshot in DB inside getCart(), so priceChanged=true
  // only appears on the FIRST fetch. We capture changes into local state so the
  // banner stays visible until the user explicitly clicks "Cập nhật giỏ hàng".
  const [detectedChanges, setDetectedChanges] = useState<CartChangeDetail[]>([]);

  const currentChanges = useMemo<CartChangeDetail[]>(() => {
    if (!cart?.sellers) return [];
    const list: CartChangeDetail[] = [];
    cart.sellers.forEach((seller) => {
      seller.items.forEach((item) => {
        const hasChanged = item.priceChanged
          || (item.priceSnapshot != null && Math.abs(item.priceSnapshot - item.unitPrice) > 0);
        if (!hasChanged) return;
        list.push({
          variantId: item.variantId,
          skuCode: item.skuCode,
          productName: item.productName,
          reason: 'PRICE_CHANGED',
          currentValue: fmt(item.unitPrice),
          expectedValue: fmt(item.priceSnapshot ?? item.unitPrice),
        });
      });
    });
    return list;
  }, [cart]);

  // Capture once — never overwrite existing changes unless user reloads
  useEffect(() => {
    if (currentChanges.length > 0) {
      setDetectedChanges((prev) => prev.length > 0 ? prev : currentChanges);
    }
  }, [currentChanges]);

  const [refreshLoading, setRefreshLoading] = useState(false);

  const handleRefreshCart = async () => {
    setRefreshLoading(true);
    try {
      await fetchCart();
      setDetectedChanges([]); // clear captured changes after refresh
    } finally {
      setRefreshLoading(false);
    }
  };

  const cartChanges = detectedChanges;

  if (isLoading && !cart) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-8 space-y-3">
        <Skeleton className="h-7 w-40 mb-4" />
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 bg-white rounded-2xl border border-gray-100 p-4">
            <Skeleton className="h-16 w-16 rounded-xl" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-1/3" />
            </div>
            <Skeleton className="h-5 w-20" />
          </div>
        ))}
      </div>
    );
  }

  if (!cart?.sellers || getItemCount() === 0) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <div className="w-24 h-24 rounded-full bg-blue-50 flex items-center justify-center mx-auto mb-6 text-5xl">
          🛒
        </div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Giỏ hàng trống</h2>
        <p className="text-gray-500 mb-8">Bạn chưa thêm sản phẩm nào vào giỏ hàng</p>
        <Link
          to="/products"
          className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Tiếp tục mua sắm
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <CheckoutStepper currentStep="cart" className="mb-6" />
      <h1 className="text-2xl font-bold text-gray-900 mb-6">
        Giỏ hàng ({getItemCount()} sản phẩm)
      </h1>

      {/* Price-change banner — shown when backend reports hasPriceChanges=true */}
      {cartChanges.length > 0 && (
        <CartChangeBanner
          details={cartChanges}
          onRefresh={handleRefreshCart}
          isLoading={refreshLoading}
        />
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Items */}
        <div className="lg:col-span-2 space-y-4">
          {/* Select all header */}
          <div className="bg-white rounded-2xl border border-gray-100 p-4 flex items-center gap-3">
            <input
              type="checkbox"
              checked={selectedItems.size === getItemCount() && getItemCount() > 0}
              onChange={selectAllItems}
              className="w-5 h-5 accent-blue-600 cursor-pointer"
            />
            <span className="font-semibold text-gray-900">
              Chọn tất cả ({getItemCount()})
            </span>
          </div>

          {/* Sellers groups */}
          {cart?.sellers.map((seller) => (
            <div key={seller.sellerId} className="bg-white rounded-2xl border border-gray-100 p-4">
              <p className="text-sm font-semibold text-gray-900 mb-4 pb-4 border-b">
                {seller.sellerName}
              </p>
              <div className="space-y-3">
                {seller.items.map((item) => {
                    const isExpired = item.isFlash && isFlashExpired(item.flashExpiresAt);
                    const overLimit = item.maxQuantityPerUser && item.quantity > item.maxQuantityPerUser;
                    const overStock = item.quantity > item.stockAvailable;

                    return (
                      <div key={itemKey(item)} className={`flex items-center gap-4 p-3 rounded-xl border transition-colors ${
                        isExpired ? 'border-red-200 bg-red-50/30' : item.isFlash ? 'border-orange-200 bg-orange-50/20' : 'border-gray-100'
                      }`}>
                        <input
                          type="checkbox"
                          checked={selectedItems.has(itemKey(item))}
                          onChange={() => toggleItemSelection(itemKey(item))}
                          className="w-5 h-5 accent-blue-600 cursor-pointer shrink-0"
                        />
                        <div className="w-20 h-20 rounded-xl bg-gray-100 flex items-center justify-center text-3xl shrink-0 overflow-hidden">
                          {item.image ? (
                            <>
                              <img
                                src={item.image}
                                alt={item.productName}
                                className="h-full w-full object-cover"
                                loading="lazy"
                                onError={(event) => {
                                  event.currentTarget.classList.add('hidden');
                                  event.currentTarget.nextElementSibling?.classList.remove('hidden');
                                }}
                              />
                              <span className="hidden" aria-hidden="true">🛍️</span>
                            </>
                          ) : (
                            <span aria-hidden="true">🛍️</span>
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <p className="text-xs text-gray-400">{item.variantName}</p>
                            {item.isFlash && (
                              <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${isExpired ? 'bg-red-100 text-red-600' : 'bg-orange-100 text-orange-600'}`}>
                                🔥 Flash Sale
                              </span>
                            )}
                            {isExpired && (
                              <span className="text-xs px-1.5 py-0.5 rounded-full bg-red-100 text-red-600 font-medium">Đã hết hạn</span>
                            )}
                          </div>
                          <h3 className="font-medium text-gray-900 truncate">{item.productName}</h3>
                          {item.isFlash && item.flashPrice ? (
                            <div className="flex items-center gap-2 mt-0.5">
                              <p className="text-red-600 font-bold">{fmt(item.flashPrice)}</p>
                              {item.unitPrice !== item.flashPrice && (
                                <p className="text-xs text-gray-400 line-through">{fmt(item.unitPrice)}</p>
                              )}
                            </div>
                          ) : (
                            <p className="text-red-600 font-bold mt-1">{fmt(item.unitPrice)}</p>
                          )}
                          <p className="text-xs text-gray-500 mt-0.5">
                            Kho: <span className={`font-semibold ${overStock ? 'text-red-600' : 'text-green-600'}`}>{item.stockAvailable}</span>
                            {item.maxQuantityPerUser && (
                              <span className="ml-2 text-orange-500">· Mua tối đa: {item.maxQuantityPerUser}</span>
                            )}
                          </p>
                          {overLimit && (
                            <p className="text-xs text-red-500 mt-0.5">
                              ⚠️ Số lượng vượt quá giới hạn mua ({item.maxQuantityPerUser})
                            </p>
                          )}
                          {overStock && !overLimit && (
                            <p className="text-xs text-red-500 mt-0.5">
                              ⚠️ Số lượng vượt quá tồn kho ({item.stockAvailable})
                            </p>
                          )}
                        </div>
                        <div className="flex flex-col items-center gap-1 shrink-0">
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => updateQuantity(item.cartItemId, Math.max(1, item.quantity - 1))}
                              disabled={item.quantity <= 1}
                              className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed text-gray-600 font-bold"
                            >
                              −
                            </button>
                            <span className={`w-8 text-center text-sm font-medium ${overLimit || overStock ? 'text-red-600' : 'text-gray-900'}`}>{item.quantity}</span>
                            <button
                              onClick={() => handleIncrease(item)}
                              className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 text-gray-600 font-bold"
                            >
                              +
                            </button>
                          </div>
                          {quantityError === item.variantId && (
                            <p className="text-xs text-red-500 whitespace-nowrap">Đã đạt số lượng tối đa</p>
                          )}
                        </div>
                        <button
                          onClick={() => removeFromCart(item.cartItemId)}
                          className="text-gray-300 hover:text-red-400 transition-colors shrink-0"
                        >
                          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        </button>
                      </div>
                    );
                  })}
              </div>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div className="bg-white rounded-2xl border border-gray-100 p-6 h-fit sticky top-24">
          <h3 className="font-bold text-gray-900 mb-4">Tóm tắt đơn hàng</h3>
          <div className="space-y-3 text-sm mb-6">
            {getSelectedSellerTotals().map(s => (
              <div key={s.sellerName} className="flex justify-between text-gray-600">
                <span className="truncate mr-2">{s.sellerName}</span>
                <span className="font-semibold shrink-0">{fmt(s.total)}</span>
              </div>
            ))}
            <div className="h-px bg-gray-100" />
            <div className="flex justify-between text-gray-600">
              <span>Tạm tính</span>
              <span className="font-semibold">{fmt(getTotal())}</span>
            </div>
            <div className="flex justify-between text-gray-600">
              <span>Phí vận chuyển</span>
              <span className="text-green-600 font-medium">Miễn phí</span>
            </div>
            <div className="flex justify-between text-gray-600">
              <span>Giảm giá</span>
              <span>—</span>
            </div>
            <div className="h-px bg-gray-100" />
            <div className="flex justify-between font-bold text-gray-900 text-base">
              <span>Tổng cộng</span>
              <span className="text-red-600">{fmt(getTotal())}</span>
            </div>
          </div>

          {selectedItems.size > 0 ? (
            <Link
              to="/checkout"
              state={{ selectedItemIds: Array.from(selectedItems) }}
              className="block w-full py-3 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 text-white font-semibold text-center rounded-xl transition-all"
            >
              Thanh toán ({selectedItems.size} mục)
            </Link>
          ) : (
            <button
              disabled
              className="w-full py-3 bg-gray-300 text-gray-500 font-semibold rounded-xl cursor-not-allowed"
            >
              Chọn sản phẩm để thanh toán
            </button>
          )}

          <Link
            to="/products"
            className="block w-full mt-3 py-2 border border-gray-200 text-gray-700 font-semibold text-center rounded-xl hover:border-gray-300 transition-all"
          >
            Tiếp tục mua sắm
          </Link>
        </div>
      </div>
    </div>
  );
}
