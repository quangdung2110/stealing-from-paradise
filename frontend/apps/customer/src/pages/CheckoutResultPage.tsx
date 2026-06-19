import { useEffect, useState } from 'react';
import { Link, useSearchParams, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import CheckoutStepper from '@/components/CheckoutStepper';
import OrderSagaTracker from '@/components/checkout/OrderSagaTracker';
import { paymentApi } from '@shared/api/payment.api';
import { orderApi } from '@shared/api/order.api';
import { useCartStore } from '@shared/store/cartStore';
import { Skeleton } from '@shared/components/ui';
import type { CheckoutResponse } from '@shared/api/order.api';
const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function recoverStoredOrderData(): CheckoutResponse | null {
  try {
    const stored = sessionStorage.getItem('pending_checkout');
    if (stored) return JSON.parse(stored) as CheckoutResponse;
  } catch (_) {}
  return null;
}

export default function CheckoutResultPage() {
  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { clearCart } = useCartStore();

  // Stripe redirect params (available on direct URL access)
  const paymentIntentId = params.get('payment_intent');
  const redirectStatus = params.get('redirect_status'); // 'succeeded' | 'failed'

  // location.state carries context from the payment page
  const locationState = location.state as {
    parentOrderId?: number;
    paymentIntentId?: string;
    method?: string;
    error?: string;
    orderData?: CheckoutResponse;
  } | null;

  // Attempt full recovery on first render (direct URL access)
  const [recoveredData, setRecoveredData] = useState<{
    parentOrderId: number;
    paymentIntentId: string;
    method: string;
    orderData: CheckoutResponse | null;
  } | null>(null);

  useEffect(() => {
    // If we have location state, no recovery needed
    if (locationState?.parentOrderId) {
      setRecoveredData({
        parentOrderId: locationState.parentOrderId,
        paymentIntentId: locationState.paymentIntentId ?? paymentIntentId ?? '',
        method: locationState.method ?? 'stripe',
        orderData: locationState.orderData ?? null,
      });
      return;
    }

    // Try sessionStorage recovery (e.g., page was refreshed after checkout)
    const stored = recoverStoredOrderData();
    if (stored?.parentOrderId) {
      setRecoveredData({
        parentOrderId: stored.parentOrderId,
        paymentIntentId: paymentIntentId ?? '',
        // COD info stored alongside orderData; fall back to 'stripe'
        method: (stored as any)._paymentMethod ?? 'stripe',
        orderData: stored,
      });
      return;
    }

    // No data at all — treat as failed
    if (!locationState?.parentOrderId && !stored) {
      setRecoveredData({
        parentOrderId: 0,
        paymentIntentId: '',
        method: 'stripe',
        orderData: null,
      });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Only run once on mount

  const state = recoveredData;
  const isExpired = redirectStatus === 'expired' || params.get('status') === 'expired' || locationState?.error?.includes('hết thời gian');
  const success = !isExpired && (redirectStatus === 'succeeded' || (locationState?.parentOrderId && !locationState?.error));

  // Persist orderData to sessionStorage so retries work after refresh
  useEffect(() => {
    if (locationState?.orderData) {
      try { sessionStorage.setItem('pending_checkout', JSON.stringify(locationState.orderData)); } catch (_) {}
    }
  }, [locationState?.orderData]);

  useEffect(() => {
    if (success && state?.parentOrderId) {
      clearCart();
      try { sessionStorage.removeItem('pending_checkout'); } catch (_) {}
    }
  }, [success, state?.parentOrderId, clearCart]);

  // Poll payment status
  const { data: paymentData } = useQuery({
    queryKey: ['payment', state?.parentOrderId],
    queryFn: () => paymentApi.getPayment(state!.parentOrderId).then(r => r.data.data),
    enabled: !!state?.parentOrderId,
    refetchInterval: (query) => {
      const payment = query.state.data;
      if (!payment) return 2000;
      if (payment.status === 'SUCCESS' || payment.status === 'FAILED') return false;
      return 2000;
    },
    retry: 1,
  });

  // Poll order status (for COD)
  const { data: polledOrder } = useQuery({
    queryKey: ['parent-order-status', state?.parentOrderId],
    queryFn: () => orderApi.getParentOrder(state!.parentOrderId).then(r => r.data.data),
    enabled: !!state?.parentOrderId && state.method === 'COD',
    refetchInterval: (query) => {
      const od = query.state.data;
      if (!od) return 5000;
      if (od.status !== 'PENDING') return false;
      return 8000;
    },
  });

  const isPending = paymentData?.status === 'PENDING';

  // Still recovering data on mount
  if (!state) {
    return (
      <div className="max-w-lg mx-auto px-4 py-20 text-center text-gray-400">
        <CheckoutStepper currentStep="complete" className="mb-8 text-gray-900" />
        <div className="bg-white rounded-2xl border border-gray-100 p-6 space-y-4">
          <Skeleton className="h-16 w-16 rounded-full mx-auto" />
          <Skeleton className="h-5 w-48 mx-auto" />
          <Skeleton className="h-4 w-64 mx-auto" />
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto px-4 py-20 text-center">
      <CheckoutStepper currentStep="complete" className="mb-8" />

      {success ? (
        <>
          <div className="w-24 h-24 rounded-full bg-green-50 flex items-center justify-center mx-auto mb-6 text-5xl">
            ✅
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-2">Đặt hàng thành công!</h1>
          <p className="text-gray-500 mb-2">
            Cảm ơn bạn đã mua hàng. Dưới đây là tiến trình đơn hàng của bạn.
          </p>

          {state.method === 'COD' && polledOrder && (
            <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-4 mb-6 text-left text-sm text-yellow-800">
              <strong>Thanh toán khi nhận hàng (COD)</strong>
              <p className="mt-1">Bạn sẽ thanh toán {fmt(polledOrder.finalAmt)} khi nhận được hàng.</p>
            </div>
          )}

          {paymentData && (
            <div className="bg-gray-50 rounded-xl p-4 mb-6 text-left">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-gray-500 text-xs">Mã giao dịch</p>
                  <p className="font-medium text-gray-700 font-mono text-xs">{paymentData.transRef}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-xs">Số tiền</p>
                  <p className="font-bold text-gray-900">{fmt(paymentData.amount)}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-xs">Phương thức</p>
                  <p className="font-medium text-gray-700">{paymentData.method}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-xs">Trạng thái</p>
                  <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                    paymentData.status === 'SUCCESS' ? 'bg-green-100 text-green-700' :
                    paymentData.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
                    'bg-red-100 text-red-700'
                  }`}>
                    {paymentData.status === 'SUCCESS' ? 'Thành công' :
                     paymentData.status === 'PENDING' ? 'Đang xử lý' : 'Thất bại'}
                  </span>
                </div>
                {paymentData.paidAt && (
                  <div>
                    <p className="text-gray-500 text-xs">Thanh toán lúc</p>
                    <p className="font-medium text-gray-700 text-xs">
                      {new Date(paymentData.paidAt).toLocaleString('vi-VN')}
                    </p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Saga tracker — only shown after payment confirmed */}
          {state.parentOrderId > 0 && (paymentData?.status === 'SUCCESS' || state.method === 'COD') && (
            <div className="mb-6">
              <OrderSagaTracker parentOrderId={state.parentOrderId} />
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            {state.parentOrderId > 0 && (
              <Link
                to={`/orders/${state.parentOrderId}`}
                className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
              >
                Xem đơn hàng
              </Link>
            )}
            <Link
              to="/products"
              className="px-6 py-3 border border-gray-200 hover:border-gray-300 text-gray-700 font-semibold rounded-xl transition-colors"
            >
              Tiếp tục mua sắm
            </Link>
          </div>
        </>
      ) : isExpired ? (
        <>
          <div className="w-24 h-24 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-6 text-5xl">
            ⏰
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-2">Hết thời gian thanh toán</h1>
          <p className="text-gray-500 mb-2">
            Đơn hàng của bạn đã bị huỷ do không thanh toán trong thời gian quy định.
          </p>
          <p className="text-gray-400 text-sm mb-8">
            Vui lòng tạo đơn hàng mới và thanh toán ngay để xác nhận.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link
              to="/cart"
              className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
            >
              Quay lại giỏ hàng
            </Link>
            <Link
              to="/products"
              className="px-6 py-3 border border-gray-200 hover:border-gray-300 text-gray-700 font-semibold rounded-xl transition-colors"
            >
              Tiếp tục mua sắm
            </Link>
          </div>
        </>
      ) : (
        <>
          <div className="w-24 h-24 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-6 text-5xl">
            ❌
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-2">Thanh toán thất bại</h1>
          <p className="text-gray-500 mb-2">
            {locationState?.error || 'Đã xảy ra lỗi trong quá trình thanh toán.'}
          </p>
          <p className="text-gray-400 text-sm mb-8">
            Vui lòng thử lại từ giỏ hàng.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link
              to="/cart"
              className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
            >
              Quay lại giỏ hàng
            </Link>
            <Link
              to="/products"
              className="px-6 py-3 border border-gray-200 hover:border-gray-300 text-gray-700 font-semibold rounded-xl transition-colors"
            >
              Tiếp tục mua sắm
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
