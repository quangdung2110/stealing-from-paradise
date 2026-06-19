import { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useCartStore } from '@shared/store/cartStore';
import { Elements, PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js';
import { cartApi, type CartChangeDetail, type CheckoutPreviewResponse } from '@shared/api/cart.api';
import { addressApi, type UserAddress } from '@shared/api/address.api';
import { paymentApi } from '@shared/api/payment.api';
import { orderApi } from '@shared/api/order.api';
import { Skeleton } from '@shared/components/ui';
import CheckoutStepper from '@/components/CheckoutStepper';
import { getStripe } from '@/lib/stripe';
import { buildCheckoutPaymentData } from './checkoutPaymentData';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';
const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const REASON_LABELS: Record<string, string> = {
  PRICE_CHANGED: 'Giá đã thay đổi',
  OUT_OF_STOCK: 'Hết hàng',
  INSUFFICIENT_STOCK: 'Số lượng vượt quá tồn kho',
  VARIANT_UNAVAILABLE: 'Sản phẩm không còn khả dụng',
  VARIANT_INACTIVE: 'Sản phẩm đã ngừng bán',
};

/**
 * Parse a cart-change error that the backend serialises into the ApiResponse.message field.
 *
 * Backend flow:
 *   AppException(CONFLICT, objectMapper.writeValueAsString(CheckoutPreviewError))
 *   → GlobalExceptionHandler wraps as ApiResponse.error("RES_004", jsonString)
 *   → jsonString lives in err.response.data.message, not at the top level
 */
function parseCartChangeError(err: any): { error: string; message: string; details: CartChangeDetail[] } | null {
  const data = err?.response?.data as any;
  if (!data?.message || typeof data.message !== 'string') return null;
  try {
    const parsed = JSON.parse(data.message);
    if (parsed?.error && Array.isArray(parsed?.details)) {
      return parsed;
    }
  } catch {
    // Plain string message — not a cart-change error
  }
  return null;
}


// ─── Address Form Modal ────────────────────────────────────────────────────────
function AddressFormModal({
  address,
  onClose,
  onSuccess,
}: {
  address?: UserAddress;
  onClose: () => void;
  onSuccess: (address: UserAddress) => void;
}) {
  const queryClient = useQueryClient();
  const isEdit = !!address;
  const [addressText, setAddressText] = useState(address?.fullAddress ?? '');
  const [provinceId, setProvinceId] = useState(address?.provinceId ?? 1);
  const [districtId, setDistrictId] = useState(address?.districtId ?? 1);
  const [setAsDefault, setSetAsDefault] = useState(address?.isDefault ?? true);
  const [error, setError] = useState('');

  const createMutation = useMutation({
    mutationFn: () =>
      addressApi.create({
        provinceId,
        districtId,
        fullAddress: addressText,
        isDefault: setAsDefault,
      }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['addresses'] });
      onSuccess(res.data.data!);
      onClose();
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Tạo địa chỉ thất bại');
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      addressApi.update(address!.addressId, {
        provinceId,
        districtId,
        fullAddress: addressText,
        isDefault: setAsDefault,
      }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['addresses'] });
      onSuccess(res.data.data!);
      onClose();
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Cập nhật địa chỉ thất bại');
    },
  });

  const mutate = isEdit ? updateMutation : createMutation;

  const provinces = [
    { id: 1, name: 'TP. Hồ Chí Minh' },
    { id: 2, name: 'Hà Nội' },
    { id: 3, name: 'Đà Nẵng' },
    { id: 4, name: 'Hải Phòng' },
    { id: 5, name: 'Cần Thơ' },
    { id: 6, name: 'Bình Dương' },
    { id: 7, name: 'Đồng Nai' },
  ];

  const districts: Record<number, { id: number; name: string }[]> = {
    1: [
      { id: 1, name: 'Quận 1' }, { id: 2, name: 'Quận 3' }, { id: 3, name: 'Quận 5' },
      { id: 4, name: 'Quận 7' }, { id: 5, name: 'Quận Bình Thạnh' },
      { id: 6, name: 'Thủ Đức' }, { id: 7, name: 'Gò Vấp' }, { id: 8, name: 'Tân Bình' },
    ],
    2: [
      { id: 11, name: 'Hoàn Kiếm' }, { id: 12, name: 'Ba Đình' },
      { id: 13, name: 'Đống Đa' }, { id: 14, name: 'Hai Bà Trưng' }, { id: 15, name: 'Thanh Xuân' },
    ],
    3: [
      { id: 21, name: 'Hải Châu' }, { id: 22, name: 'Thanh Khê' }, { id: 23, name: 'Sơn Trà' },
    ],
    4: [
      { id: 31, name: 'Hồng Bàng' }, { id: 32, name: 'Ngô Quyền' },
    ],
    5: [
      { id: 41, name: 'Ninh Kiều' }, { id: 42, name: 'Bình Thủy' },
    ],
    6: [
      { id: 51, name: 'Dĩ An' }, { id: 52, name: 'Thuận An' },
    ],
    7: [
      { id: 61, name: 'Biên Hòa' }, { id: 62, name: 'Long Thành' },
    ],
  };

  const currentDistricts = districts[provinceId] || [];

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-gray-900">{isEdit ? 'Sửa địa chỉ' : 'Thêm địa chỉ mới'}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl leading-none">&times;</button>
        </div>
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">
            {error}
          </div>
        )}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Tỉnh / Thành phố</label>
            <select
              value={provinceId}
              onChange={e => {
                const pid = Number(e.target.value);
                setProvinceId(pid);
                setDistrictId(districts[pid]?.[0]?.id ?? 1);
              }}
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {provinces.map(p => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Quận / Huyện</label>
            <select
              value={districtId}
              onChange={e => setDistrictId(Number(e.target.value))}
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {currentDistricts.map(d => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Địa chỉ chi tiết</label>
            <textarea
              value={addressText}
              onChange={e => setAddressText(e.target.value)}
              placeholder="Số nhà, đường, phường/xã..."
              rows={3}
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={setAsDefault}
              onChange={e => setSetAsDefault(e.target.checked)}
              className="w-4 h-4 accent-blue-600"
            />
            <span className="text-sm text-gray-700">Đặt làm địa chỉ mặc định</span>
          </label>
        </div>
        <div className="flex gap-3 mt-6">
          <button
            onClick={onClose}
            className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50"
          >
            Huỷ
          </button>
          <button
            onClick={() => mutate.mutate()}
            disabled={!addressText.trim() || mutate.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {mutate.isPending ? 'Đang lưu...' : isEdit ? 'Lưu thay đổi' : 'Lưu địa chỉ'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Delete Address Modal ───────────────────────────────────────────────────────
function DeleteAddressModal({
  address,
  onClose,
  onSuccess,
}: {
  address: UserAddress;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();
  const [error, setError] = useState('');

  const deleteMutation = useMutation({
    mutationFn: () => addressApi.remove(address.addressId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['addresses'] });
      onSuccess();
      onClose();
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Xoá địa chỉ thất bại');
    },
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-sm w-full text-center">
        <div className="text-5xl mb-4">🗑️</div>
        <h3 className="text-lg font-bold text-gray-900 mb-2">Xoá địa chỉ?</h3>
        <p className="text-sm text-gray-500 mb-1">{address.fullAddress}</p>
        <p className="text-xs text-gray-400 mb-6">Hành động này không thể hoàn tác.</p>
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4 text-left">
            {error}
          </div>
        )}
        <div className="flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50"
          >
            Huỷ
          </button>
          <button
            onClick={() => deleteMutation.mutate()}
            disabled={deleteMutation.isPending}
            className="flex-1 py-2.5 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {deleteMutation.isPending ? 'Đang xoá...' : 'Xoá địa chỉ'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Cart Change Banner ─────────────────────────────────────────────────────────
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
    <div className="bg-orange-50 border border-orange-200 rounded-2xl p-4 mb-6">
      <div className="flex items-start gap-3">
        <div className="text-2xl shrink-0">⚠️</div>
        <div className="flex-1 min-w-0">
          <h3 className="font-bold text-orange-800 mb-1">Dữ liệu giỏ hàng đã thay đổi</h3>
          <p className="text-sm text-orange-700 mb-3">
            Một số sản phẩm trong giỏ hàng có thông tin đã thay đổi. Vui lòng kiểm tra trước khi thanh toán.
          </p>
          <div className="space-y-2">
            {details.map((d, i) => (
              <div key={i} className="bg-white/70 rounded-xl p-3 text-sm">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-medium text-gray-900 truncate">
                      {d.productName || d.skuCode || d.variantId}
                    </p>
                    <p className="text-orange-600 font-medium mt-0.5">
                      {REASON_LABELS[d.reason] ?? d.reason}
                    </p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-gray-500 line-through text-xs">{d.expectedValue}</p>
                    <p className="text-red-600 font-bold">{d.currentValue}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
          <button
            onClick={onRefresh}
            disabled={isLoading}
            className="mt-3 flex items-center gap-2 px-4 py-2 bg-orange-200 hover:bg-orange-300 text-orange-800 font-semibold text-sm rounded-xl transition-colors disabled:opacity-50"
          >
            {isLoading ? (
              <>
                <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Đang làm mới...
              </>
            ) : (
              <>
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                Làm mới giỏ hàng
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Cart Change Error (inline for preview failures) ───────────────────────────
function CartChangeErrorAlert({
  message,
  details,
  onRefresh,
  isLoading,
}: {
  message: string;
  details: CartChangeDetail[];
  onRefresh: () => void;
  isLoading: boolean;
}) {
  return (
    <div className="bg-red-50 border border-red-200 rounded-2xl p-4 mb-4">
      <div className="flex items-start gap-3">
        <div className="text-2xl shrink-0">⚠️</div>
        <div className="flex-1 min-w-0">
          <h4 className="font-bold text-red-800">{message}</h4>
          {details.length > 0 && (
            <div className="mt-2 space-y-1">
              {details.map((d, i) => (
                <p key={i} className="text-sm text-red-700">
                  <span className="font-medium">
                    {d.productName || d.skuCode || d.variantId}
                  </span>
                  {' — '}
                  <span className="text-red-600 font-medium">
                    {REASON_LABELS[d.reason] ?? d.reason}
                  </span>
                  {d.expectedValue && d.currentValue && (
                    <span className="ml-1">
                      ({d.expectedValue} → <span className="font-bold">{d.currentValue}</span>)
                    </span>
                  )}
                </p>
              ))}
            </div>
          )}
          <button
            onClick={onRefresh}
            disabled={isLoading}
            className="mt-3 flex items-center gap-2 px-4 py-2 bg-red-100 hover:bg-red-200 text-red-700 font-semibold text-sm rounded-xl transition-colors disabled:opacity-50"
          >
            {isLoading ? 'Đang làm mới...' : 'Làm mới giỏ hàng'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Session Expired / Cart Changed Dialog ─────────────────────────────────────
function CheckoutSessionExpiredDialog({
  isExpired,
  message,
  details,
  onGoToCart,
}: {
  isExpired: boolean;
  message: string;
  details: CartChangeDetail[];
  onGoToCart: () => void;
}) {
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md text-center">
        <div className="text-5xl mb-4">{isExpired ? '⏰' : '⚠️'}</div>
        <h3 className="text-lg font-bold text-gray-900 mb-2">
          {isExpired ? 'Phiên mua hàng đã hết hạn' : 'Thông tin sản phẩm thay đổi'}
        </h3>
        <p className="text-sm text-gray-500 mb-1">{message}</p>
        {details.length > 0 && (
          <div className="mt-3 space-y-1.5 text-left bg-gray-50 rounded-xl p-3 max-h-40 overflow-y-auto">
            {details.map((d, i) => (
              <p key={i} className="text-xs text-gray-600">
                <span className="font-medium text-gray-800">
                  {d.productName || d.skuCode || d.variantId}
                </span>
                {' — '}
                <span className="text-orange-600 font-medium">
                  {REASON_LABELS[d.reason] ?? d.reason}
                </span>
                {d.expectedValue && d.currentValue && (
                  <span className="ml-1">
                    ({d.expectedValue} → <span className="font-bold">{d.currentValue}</span>)
                  </span>
                )}
              </p>
            ))}
          </div>
        )}
        <p className="text-xs text-gray-400 mt-3 mb-6">
          Vui lòng quay lại giỏ hàng để xem thông tin sản phẩm mới nhất.
        </p>
        <button
          onClick={onGoToCart}
          className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl transition-colors"
        >
          Quay lại giỏ hàng
        </button>
      </div>
    </div>
  );
}

// ─── Main Page ─────────────────────────────────────────────────────────────────
export default function OrderReviewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { cart, fetchCart, isLoading: cartLoading } = useCartStore();

  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [step, setStep] = useState<'address' | 'review' | 'payment'>('address');
  const [paymentMethod, setPaymentMethod] = useState<'stripe' | 'cod'>('stripe');
  const [isProcessing, setIsProcessing] = useState(false);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [editingAddress, setEditingAddress] = useState<UserAddress | undefined>(undefined);
  const [deletingAddress, setDeletingAddress] = useState<UserAddress | null>(null);
  const [showNoDefaultDialog, setShowNoDefaultDialog] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);

  // Inline payment state
  const [paymentStep, setPaymentStep] = useState<'idle' | 'submitting' | 'paying' | 'processing'>('idle');
  const [parentOrderId, setParentOrderId] = useState<number | null>(null);
  const [orderData, setOrderData] = useState<Record<string, any> | null>(null);
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [stripeError, setStripeError] = useState<string | null>(null);

  // Checkout flow state
  const [previewData, setPreviewData] = useState<CheckoutPreviewResponse | null>(null);
  const [previewToken, setPreviewToken] = useState<string | null>(null);
  const [cartChanges, setCartChanges] = useState<CartChangeDetail[]>([]);
  const [refreshLoading, setRefreshLoading] = useState(false);

  const selectedItemIds = (location.state?.selectedItemIds || []) as string[];
  const matchesSelectedItem = (item: { cartItemId?: string; variantId?: string }) =>
    selectedItemIds.includes(item.cartItemId ?? '') || selectedItemIds.includes(item.variantId ?? '');

  // Check for price changes on cart load
  useEffect(() => {
    if (cart?.hasPriceChanges) {
      const changedItems: CartChangeDetail[] = [];
      cart.sellers.forEach(seller => {
        seller.items.forEach(item => {
          changedItems.push({
            variantId: item.variantId,
            skuCode: item.skuCode,
            productName: item.productName,
            reason: 'PRICE_CHANGED',
            currentValue: fmt(item.unitPrice),
            expectedValue: fmt(item.unitPrice),
          });
        });
      });
      setCartChanges(prev => [...prev, ...changedItems]);
    }
  }, [cart]);

  const getItemPrice = (item: any) => {
    if (item.isFlash && item.flashPrice) return item.flashPrice;
    return item.unitPrice;
  };

  const getSelectedItemsData = () => {
    if (!cart || selectedItemIds.length === 0) return [];
    const items: any[] = [];
    cart.sellers.forEach(seller => {
      seller.items.forEach(item => {
        if (matchesSelectedItem(item)) {
          items.push({ ...item, sellerName: seller.sellerName, price: getItemPrice(item) });
        }
      });
    });
    return items;
  };

  const selectedItems = getSelectedItemsData();
  const subtotal = selectedItems.reduce((sum, item) => sum + item.price * item.quantity, 0);

  const { data: addresses = [], isLoading: addrsLoading } = useQuery({
    queryKey: ['addresses'],
    queryFn: () => addressApi.list().then(r => r.data.data ?? []),
    retry: 1,
  });

  useEffect(() => {
    if (addresses.length > 0 && !selectedAddressId) {
      const def = addresses.find(a => a.isDefault);
      setSelectedAddressId(def?.addressId ?? addresses[0].addressId);
    }
    if (addresses.length > 0) {
      const hasDefault = addresses.some(a => a.isDefault);
      if (!hasDefault) {
        setShowNoDefaultDialog(true);
      }
    }
  }, [addresses, selectedAddressId]);

  const handleAddressCreated = (newAddr: UserAddress) => {
    setSelectedAddressId(newAddr.addressId);
  };

  const handleRefreshCart = async () => {
    if (cartChanges.length > 0) {
      setRefreshLoading(true);
      try {
        // Fetch latest cart data first, then navigate
        await fetchCart();
      } catch {
        // Proceed even if fetch fails
      } finally {
        setRefreshLoading(false);
      }
      navigate('/cart');
      return;
    }
    setRefreshLoading(true);
    setCartChanges([]);
    setApiError(null);
    try {
      await fetchCart();
    } finally {
      setRefreshLoading(false);
    }
  };

  // Step 1: Preview → validate cart + get preview token
  const handleCreateOrder = async () => {
    if (!selectedAddressId || selectedItemIds.length === 0) return;
    if (addresses.length === 0) {
      setShowNoDefaultDialog(true);
      return;
    }
    setIsProcessing(true);
    setApiError(null);
    setCartChanges([]);
    try {
      // Build proper "customerId:variantId" keys from cart items
      const itemKeys: string[] = [];
      const customerId = cart?.userId;
      cart?.sellers.forEach(seller => {
        seller.items.forEach(item => {
          if (matchesSelectedItem(item)) {
            itemKeys.push(item.cartItemId);
          }
        });
      });
      const { data } = await cartApi.checkoutPreview(itemKeys);
      if (data.data) {
        setPreviewData(data.data);
        setPreviewToken(data.data.previewToken);
        setStep('review');
      }
    } catch (err: any) {
      const cartError = parseCartChangeError(err);
      if (cartError) {
        setCartChanges(cartError.details as CartChangeDetail[]);
      } else {
        setApiError(
          err?.response?.data?.message ||
          err?.response?.data?.errorCode ||
          'Lỗi xác thực giỏ hàng'
        );
      }
    } finally {
      setIsProcessing(false);
    }
  };

  // Step 2: Submit → poll parentOrderId → poll client secret → Stripe
  const handleProceedToPayment = async () => {
    if (!previewToken || !selectedAddressId || !previewData) return;
    const addr = addresses.find(a => a.addressId === selectedAddressId);
    setIsProcessing(true);
    setApiError(null);
    setStripeError(null);
    try {
      // Snapshot max parentOrderId BEFORE submit (order is created async via Kafka)
      const { data: preOrders } = await orderApi.getOrders({ page: 0, size: 200 });
      const preMax = Math.max(
        0,
        ...((preOrders?.data?.data?.content ?? preOrders?.data?.data ?? []) as any[]).map(
          (o: any) => o.parentOrderId ?? 0
        ),
      );

      const { data } = await cartApi.checkoutSubmit(
        previewToken,
        selectedAddressId,
        addr?.provinceId,
        addr?.districtId,
        addr?.fullAddress,
      );
      if (data.data) {
        const submitResp = data.data;
        const orderData_ = buildCheckoutPaymentData(submitResp, previewData);
        sessionStorage.setItem('pending_checkout', JSON.stringify(orderData_));
        setOrderData(orderData_);

        if (paymentMethod === 'cod') {
          // Poll for the async-created parentOrderId
          const poId = await pollForNewParentOrder(preMax);
          navigate('/checkout/result?status=success', {
            state: { parentOrderId: poId, method: 'COD', orderData: orderData_ },
          });
          return;
        }

        // Stripe: wait for parentOrderId (async via Kafka) → poll client secret
        setPaymentStep('paying');
        const poId = await pollForNewParentOrder(preMax);
        setParentOrderId(poId);
        await pollForClientSecret(poId);
      }
    } catch (err: any) {
      const cartError = parseCartChangeError(err);
      const isExpired =
        err?.response?.status === 404 ||
        cartError?.error === 'PREVIEW_TOKEN_EXPIRED' ||
        (err?.response?.data?.message && String(err?.response?.data?.message).includes('không tồn tại') && String(err?.response?.data?.message).includes('hết hạn'));
      if (isExpired) {
        setSessionExpired(true);
        return;
      }
      if (cartError) {
        setCartChanges(cartError.details as CartChangeDetail[]);
      } else {
        setApiError(
          err?.response?.data?.message ||
          err?.response?.data?.errorCode ||
          (err?.message === 'ORDER_NOT_READY' ? 'Đơn hàng đang được tạo. Vui lòng thử lại sau vài giây.' : null) ||
          'Lỗi tạo đơn hàng'
        );
      }
    } finally {
      setIsProcessing(false);
    }
  };

  // Poll for new parentOrderId (order is created asynchronously by order-service Kafka consumer)
  const pollForNewParentOrder = async (preMax: number): Promise<number> => {
    for (let attempt = 0; attempt < 30; attempt++) {
      await sleep(1000);
      try {
        const { data } = await orderApi.getOrders({ page: 0, size: 200 });
        const orders = (data?.data?.content ?? data?.data ?? []) as any[];
        for (const o of orders) {
          if ((o.parentOrderId ?? 0) > preMax) {
            return o.parentOrderId as number;
          }
        }
      } catch {
        // retry
      }
    }
    throw new Error('ORDER_NOT_READY');
  };

  // Poll for client secret (payment intent created asynchronously by payment-service Kafka consumer)
  const pollForClientSecret = async (poId: number) => {
    for (let attempt = 0; attempt < 30; attempt++) {
      try {
        const { data } = await paymentApi.getClientSecret(poId);
        if (data.data?.clientSecret) {
          setClientSecret(data.data.clientSecret);
          return;
        }
        // 202 Accepted means "still initializing" — wait and retry
      } catch (err: any) {
        if (err?.response?.status === 404) {
          // Not ready yet, wait
        } else {
          throw err;
        }
      }
      await sleep(1000);
    }
    setStripeError('Không thể khởi tạo cổng thanh toán. Vui lòng thử lại sau.');
  };

  const selectedAddress = addresses.find(a => a.addressId === selectedAddressId);

  // Stripe payment success → navigate to result with saga tracker
  const handleStripeSuccess = (paymentIntentId: string) => {
    if (!parentOrderId) return;
    navigate('/checkout/result?status=success', {
      state: {
        parentOrderId,
        paymentIntentId,
        method: 'stripe',
        orderData,
      },
    });
  };

  // Inline Stripe payment form
  const InlineStripeForm = () => {
    const stripe = useStripe();
    const elements = useElements();
    const [isPaying, setIsPaying] = useState(false);

    const handlePay = async (e: React.FormEvent) => {
      e.preventDefault();
      if (!stripe || !elements) return;
      setIsPaying(true);
      setStripeError(null);

      const { error, paymentIntent } = await stripe.confirmPayment({
        elements,
        confirmParams: {
          return_url: `${window.location.origin}/checkout/result`,
        },
        redirect: 'if_required',
      });

      if (error) {
        setStripeError(error.message ?? 'Thanh toán thất bại');
        setIsPaying(false);
      } else if (paymentIntent && paymentIntent.status === 'succeeded') {
        setPaymentStep('processing');
        handleStripeSuccess(paymentIntent.id);
      }
    };

    return (
      <form onSubmit={handlePay} className="space-y-4">
        <div className="bg-white rounded-2xl border border-gray-100 p-6">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <span>💳</span>
            Thông tin thẻ tín dụng
          </h2>
          <div className="bg-gray-50 rounded-xl p-4 mb-4 border border-gray-200">
            <PaymentElement
              options={{
                layout: 'tabs',
                paymentMethodOrder: ['card'],
              }}
            />
          </div>
          <p className="text-xs text-gray-400 mb-4">
            Thử nghiệm: Dùng thẻ test Stripe (4242 4242 4242 4242), bất kỳ exp/CVC nào
          </p>
          {stripeError && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-3 mb-4">
              <p className="text-red-700 text-sm">{stripeError}</p>
            </div>
          )}
          <button
            type="submit"
            disabled={!stripe || isPaying}
            className="w-full py-3.5 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-semibold rounded-xl transition-all flex items-center justify-center gap-2"
          >
            {isPaying ? (
              <>
                <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Đang xử lý thanh toán...
              </>
            ) : (
              `Thanh toán ${fmt(previewData?.totalAmount ?? 0)}`
            )}
          </button>
        </div>
      </form>
    );
  };

  return (
    <div className="bg-gray-50 min-h-screen py-8">
      <div className="max-w-5xl mx-auto px-4 sm:px-6">
        <CheckoutStepper currentStep="review" className="mb-6" />

        {/* Cart change warning banner (shown on page load if cart has changes) */}
        {cartChanges.length > 0 && step === 'address' && (
          <CartChangeBanner
            details={cartChanges}
            onRefresh={handleRefreshCart}
            isLoading={refreshLoading}
          />
        )}

        {/* Inline error (shown after preview/submit failures) */}
        {apiError && (
          <CartChangeErrorAlert
            message={apiError}
            details={cartChanges}
            onRefresh={handleRefreshCart}
            isLoading={refreshLoading}
          />
        )}

        {/* Step indicator */}
        <div className="mb-8 flex items-center justify-between">
          {[
            { id: 'address', label: 'Địa chỉ' },
            { id: 'review', label: 'Xem lại' },
            { id: 'payment', label: 'Thanh toán' },
          ].map((s, i, arr) => {
            const stepOrder = ['address', 'review', 'payment'];
            const currentIdx = stepOrder.indexOf(step);
            return (
              <div key={s.id} className="flex items-center flex-1">
                <div
                  className={`w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm shrink-0 ${
                    step === s.id
                      ? 'bg-blue-600 text-white'
                      : currentIdx > i
                        ? 'bg-green-600 text-white'
                        : 'bg-gray-200 text-gray-600'
                  }`}
                >
                  {currentIdx > i ? '✓' : i + 1}
                </div>
                <span className="ml-2 font-medium text-gray-900 hidden sm:inline">{s.label}</span>
                {i < arr.length - 1 && (
                  <div className="flex-1 h-1 mx-2 bg-gray-200 ml-4" />
                )}
              </div>
            );
          })}
        </div>

        {step === 'address' && (
          <div className="max-w-3xl">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-2xl font-bold text-gray-900">Chọn địa chỉ giao hàng</h2>
              <button
                onClick={() => { setEditingAddress(undefined); setShowAddressForm(true); }}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-xl transition-colors flex items-center gap-1"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                Thêm địa chỉ
              </button>
            </div>

            {addrsLoading && (
              <div className="space-y-3 mb-8">
                {[0, 1, 2].map(i => (
                  <div key={i} className="flex items-start p-4 border-2 border-gray-100 rounded-xl bg-white">
                    <Skeleton className="w-5 h-5 mt-1 rounded-full shrink-0" />
                    <div className="ml-4 flex-1 space-y-2">
                      <Skeleton className="h-4 w-3/4" />
                      <Skeleton className="h-3 w-1/2" />
                    </div>
                    <Skeleton className="h-8 w-20 rounded-lg shrink-0" />
                  </div>
                ))}
              </div>
            )}

            {!addrsLoading && addresses.length === 0 && (
              <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-6 text-center mb-6">
                <p className="text-yellow-700 mb-3">Bạn chưa có địa chỉ giao hàng.</p>
                <button
                  onClick={() => setShowAddressForm(true)}
                  className="inline-block px-4 py-2 bg-blue-600 text-white text-sm rounded-xl hover:bg-blue-700"
                >
                  Thêm địa chỉ ngay
                </button>
              </div>
            )}

            {!addrsLoading && addresses.length > 0 && (
              <div className="space-y-3 mb-8">
                {addresses.map(addr => (
                  <div
                    key={addr.addressId}
                    className={`flex items-start p-4 border-2 rounded-xl transition-all ${
                      addr.addressId === selectedAddressId
                        ? 'border-blue-500 bg-blue-50'
                        : 'border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    <input
                      type="radio"
                      name="address"
                      checked={selectedAddressId === addr.addressId}
                      onChange={() => setSelectedAddressId(addr.addressId)}
                      className="w-5 h-5 mt-1 accent-blue-600 shrink-0"
                    />
                    <div className="ml-4 flex-1">
                      <p className="font-semibold text-gray-900">{addr.fullAddress}</p>
                      {addr.isDefault && (
                        <span className="inline-block mt-2 px-2 py-1 bg-green-100 text-green-800 text-xs rounded-full font-medium">
                          Địa chỉ mặc định
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        onClick={() => {
                          setEditingAddress(addr);
                          setShowAddressForm(true);
                        }}
                        className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        title="Sửa"
                      >
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                        </svg>
                      </button>
                      <button
                        onClick={() => setDeletingAddress(addr)}
                        className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                        title="Xoá"
                      >
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Order items summary */}
            {selectedItems.length > 0 && (
              <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-bold text-gray-900">Sản phẩm cần giao ({selectedItems.length} sản phẩm)</h3>
                  <button
                    onClick={handleRefreshCart}
                    disabled={refreshLoading || cartLoading}
                    className="text-sm text-blue-600 hover:text-blue-700 font-medium flex items-center gap-1 disabled:opacity-50"
                  >
                    <svg className={`w-3.5 h-3.5 ${refreshLoading ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    Làm mới
                  </button>
                </div>
                <div className="space-y-3">
                  {selectedItems.map(item => (
                    <div key={item.cartItemId} className="flex items-center justify-between pb-3 border-b last:border-b-0 last:pb-0">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{item.productName}</p>
                        <p className="text-xs text-gray-500">{item.variantName} × {item.quantity}</p>
                        <p className="text-xs text-gray-500">{item.sellerName}</p>
                      </div>
                      <p className="font-semibold text-gray-900 ml-4">{fmt(item.price * item.quantity)}</p>
                    </div>
                  ))}
                </div>
                <div className="mt-4 pt-4 border-t space-y-2">
                  <div className="flex justify-between text-gray-600">
                    <span>Tạm tính</span>
                    <span>{fmt(subtotal)}</span>
                  </div>
                  <div className="flex justify-between text-gray-600">
                    <span>Phí vận chuyển</span>
                    <span className="text-green-600 font-medium">Miễn phí</span>
                  </div>
                  <div className="h-px bg-gray-100" />
                  <div className="flex justify-between font-bold text-base">
                    <span>Tổng cộng</span>
                    <span className="text-red-600">
                      {fmt(subtotal)}
                    </span>
                  </div>
                </div>
              </div>
            )}

            <button
              onClick={handleCreateOrder}
              disabled={isProcessing || !selectedAddressId || selectedItemIds.length === 0}
              className="w-full py-4 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white font-bold rounded-xl transition-colors flex items-center justify-center gap-2"
            >
              {isProcessing ? (
                <>
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  Đang kiểm tra giỏ hàng...
                </>
              ) : (
                'Tiếp tục'
              )}
            </button>
          </div>
        )}

        {step === 'review' && previewData && paymentStep !== 'paying' && (
          <div className="max-w-3xl">
            {/* Cart change warnings on review step */}
            <div className="flex items-center gap-3 mb-6">
              <button
                onClick={() => { setStep('address'); setSessionExpired(false); setCartChanges([]); setApiError(null); }}
                className="text-gray-400 hover:text-gray-600 text-2xl"
              >
                ←
              </button>
              <h2 className="text-2xl font-bold text-gray-900">Xem lại đơn hàng</h2>
            </div>

            {selectedAddress && (
              <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
                <h3 className="font-bold text-gray-900 mb-2">📍 Địa chỉ giao hàng</h3>
                <p className="text-gray-700">{selectedAddress.fullAddress}</p>
                <button
                  onClick={() => { setStep('address'); setSessionExpired(false); setCartChanges([]); setApiError(null); }}
                  className="text-sm text-blue-600 hover:underline mt-2"
                >
                  Thay đổi
                </button>
              </div>
            )}

            <div className="space-y-6 mb-6">
              {previewData.sellers.map((sellerGroup) => (
                <div key={sellerGroup.sellerId} className="bg-white rounded-2xl border border-gray-100 p-6">
                  <div className="flex items-center justify-between mb-4 pb-4 border-b">
                    <div>
                      <p className="font-bold text-gray-900">
                        {sellerGroup.sellerName || `Seller #${sellerGroup.sellerId}`}
                      </p>
                    </div>
                    <p className="font-bold text-lg">{fmt(sellerGroup.subtotal)}</p>
                  </div>
                  <div className="space-y-3">
                    {sellerGroup.items.map((item) => (
                      <div key={item.variantId} className="flex items-center gap-3">
                        <div className="w-12 h-12 rounded-lg bg-gray-100 flex items-center justify-center text-xl shrink-0 overflow-hidden">
                          {item.imageUrl ? (
                            <>
                              <img
                                src={item.imageUrl}
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
                          <p className="text-sm font-medium text-gray-900 truncate">{item.productName}</p>
                          <p className="text-xs text-gray-500">{item.variantName} × {item.quantity}</p>
                        </div>
                        <p className="font-semibold text-gray-900 shrink-0">{fmt(item.subtotal)}</p>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>

            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <div className="space-y-3 text-sm mb-4">
                <div className="flex justify-between text-gray-600">
                  <span>Tạm tính</span>
                  <span>{fmt(previewData.totalAmount)}</span>
                </div>
                <div className="flex justify-between text-gray-600">
                  <span>Phí vận chuyển</span>
                  <span className="text-green-600 font-medium">Miễn phí</span>
                </div>
                <div className="h-px bg-gray-100" />
                <div className="flex justify-between font-bold text-base">
                  <span>Tổng thanh toán</span>
                  <span className="text-red-600 text-lg">{fmt(previewData.totalAmount)}</span>
                </div>
              </div>
            </div>

            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <h3 className="font-bold text-gray-900 mb-4">Phương thức thanh toán</h3>
              <div className="space-y-3">
                {[
                  { id: 'stripe', label: 'Thẻ tín dụng / Visa / Mastercard', icon: '💳', desc: 'Thanh toán an toàn qua Stripe' },
                  { id: 'cod', label: 'Thanh toán khi nhận hàng (COD)', icon: '💵', desc: 'Trả tiền mặt khi nhận hàng' },
                ].map(({ id, label, icon, desc }) => (
                  <label
                    key={id}
                    className={`flex items-center gap-4 p-4 border-2 rounded-xl cursor-pointer transition-all ${
                      paymentMethod === id ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    <input
                      type="radio"
                      name="payment"
                      value={id}
                      checked={paymentMethod === id}
                      onChange={e => setPaymentMethod(e.target.value as 'stripe' | 'cod')}
                      className="accent-blue-600 shrink-0"
                    />
                    <span className="text-2xl">{icon}</span>
                    <div>
                      <p className="text-sm font-medium text-gray-900">{label}</p>
                      <p className="text-xs text-gray-500">{desc}</p>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            <button
              onClick={handleProceedToPayment}
              disabled={isProcessing || cartChanges.length > 0}
              className="w-full py-4 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-bold rounded-xl transition-all flex items-center justify-center gap-2"
            >
              {isProcessing ? (
                <>
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  Đang xử lý...
                </>
              ) : paymentMethod === 'cod' ? (
                `Xác nhận đặt hàng (COD)`
              ) : (
                `Thanh toán ${fmt(previewData.totalAmount)}`
              )}
            </button>
          </div>
        )}

        {/* Inline Stripe Payment Step */}
        {step === 'review' && paymentStep === 'paying' && (
          <div className="max-w-3xl mt-6">
            <div className="flex items-center gap-3 mb-6">
              <h2 className="text-2xl font-bold text-gray-900">Thanh toán</h2>
            </div>

            {!clientSecret ? (
              <div className="bg-white rounded-2xl border border-gray-100 p-6">
                <div className="flex items-center gap-3 mb-3">
                  <Skeleton className="h-5 w-5 rounded-full" />
                  <Skeleton className="h-5 w-64" />
                </div>
                <div className="space-y-2 mb-5">
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-4 w-5/6" />
                </div>
                <Skeleton className="h-12 w-full rounded-xl" />
                <p className="text-center text-sm text-gray-500 mt-4">
                  Đang khởi tạo cổng thanh toán...
                </p>
              </div>
            ) : (
              <div>
                <Elements stripe={getStripe()} options={{ clientSecret }}>
                  <InlineStripeForm />
                </Elements>
                <button
                  onClick={() => { setPaymentStep('idle'); setClientSecret(null); }}
                  className="mt-4 text-sm text-gray-500 hover:text-gray-700 underline"
                >
                  ← Quay lại
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {showNoDefaultDialog && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl p-6 w-full max-w-sm text-center">
            <div className="text-5xl mb-4">📍</div>
            <h3 className="text-lg font-bold text-gray-900 mb-2">Bạn chưa có địa chỉ giao hàng</h3>
            <p className="text-sm text-gray-500 mb-6">
              Vui lòng thêm địa chỉ giao hàng trước khi tiếp tục thanh toán.
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setShowNoDefaultDialog(false)}
                className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50"
              >
                Đóng
              </button>
              <button
                onClick={() => {
                  setShowNoDefaultDialog(false);
                  setEditingAddress(undefined);
                  setShowAddressForm(true);
                }}
                className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium"
              >
                Thêm địa chỉ
              </button>
            </div>
          </div>
        </div>
      )}

      {(sessionExpired || (cartChanges.length > 0 && step === 'review')) && (
        <CheckoutSessionExpiredDialog
          isExpired={sessionExpired}
          message={sessionExpired
            ? 'Phiên xem trước đơn hàng đã hết hạn. Vui lòng làm mới giỏ hàng.'
            : 'Một số sản phẩm trong giỏ hàng có thông tin đã thay đổi.'}
          details={cartChanges}
          onGoToCart={() => navigate('/cart')}
        />
      )}

      {showAddressForm && (
        <AddressFormModal
          address={editingAddress}
          onClose={() => { setShowAddressForm(false); setEditingAddress(undefined); }}
          onSuccess={handleAddressCreated}
        />
      )}

      {deletingAddress && (
        <DeleteAddressModal
          address={deletingAddress}
          onClose={() => setDeletingAddress(null)}
          onSuccess={() => {
            if (deletingAddress.addressId === selectedAddressId) {
              setSelectedAddressId(null);
            }
          }}
        />
      )}
    </div>
  );
}
