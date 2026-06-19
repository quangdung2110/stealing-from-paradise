import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Elements, PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js';
import CheckoutStepper from '@/components/CheckoutStepper';
import { getStripe } from '@/lib/stripe';
import { paymentApi } from '@shared/api/payment.api';
import { orderApi } from '@shared/api/order.api';
import { Skeleton } from '@shared/components/ui';
import { normalizeCheckoutPaymentData, type CheckoutPaymentData } from './checkoutPaymentData';
import {
  getClientSecretErrorMessage,
  getClientSecretPanelState,
  getClientSecretRetryDelay,
  shouldPollClientSecret,
  shouldRetryClientSecret,
} from './paymentClientSecretQuery';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function PaymentForm({
  amount,
  onSuccess,
}: {
  amount: number;
  onSuccess: (piId: string) => void;
}) {
  const stripe = useStripe();
  const elements = useElements();
  const [isProcessing, setIsProcessing] = useState(false);
  const [stripeError, setStripeError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!stripe || !elements) return;
    setIsProcessing(true);
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
      setIsProcessing(false);
    } else if (paymentIntent && paymentIntent.status === 'succeeded') {
      onSuccess(paymentIntent.id);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <span className="w-6 h-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center font-bold">
            💳
          </span>
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
          Thử nghiệm: Dùng thẻ test Stripe (4242 4242 4242 4242), bất kỳ exp/CVC nào, zip 42424
        </p>
        {stripeError && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-3 mb-4">
            <p className="text-red-700 text-sm">{stripeError}</p>
          </div>
        )}
        <button
          type="submit"
          disabled={!stripe || isProcessing}
          className="w-full py-3.5 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-semibold rounded-xl transition-all flex items-center justify-center gap-2"
        >
          {isProcessing ? (
            <>
              <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Đang xử lý thanh toán...
            </>
          ) : (
            `Thanh toán ${fmt(amount)}`
          )}
        </button>
      </div>
    </form>
  );
}

export default function CheckoutPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [orderData, setOrderData] = useState<CheckoutPaymentData | null>(
    (location.state?.orderData as CheckoutPaymentData) || null
  );

  // Recover from sessionStorage if no location state (e.g., navigated here directly)
  useEffect(() => {
    if (!orderData) {
      try {
        const stored = sessionStorage.getItem('pending_checkout');
        if (stored) {
          const parsed: CheckoutPaymentData = JSON.parse(stored);
          setOrderData(parsed);
          return;
        }
      } catch (_) {}
      navigate('/cart');
    }
  }, [orderData, navigate]);

  const parentOrderId = orderData?.parentOrderId;

  // Fetch parent order to get shipping address
  const { data: parentOrder } = useQuery({
    queryKey: ['parent-order', parentOrderId],
    queryFn: () => orderApi.getParentOrder(parentOrderId!).then(r => r.data.data),
    enabled: !!parentOrderId,
    retry: 1,
  });

  const normalizedOrderData = normalizeCheckoutPaymentData(orderData, parentOrder ?? null);

  const {
    data: clientSecretData,
    error: clientSecretError,
    failureCount: clientSecretFailureCount,
    isFetching: secretFetching,
    isPending: secretPending,
  } = useQuery({
    queryKey: ['client-secret', parentOrderId],
    queryFn: () =>
      paymentApi.getClientSecret(parentOrderId!).then(r => r.data.data ?? null),
    enabled: !!parentOrderId && !!orderData,
    retry: shouldRetryClientSecret,
    retryDelay: getClientSecretRetryDelay,
    refetchInterval: query => shouldPollClientSecret(query.state.data, query.state.error),
  });

  const handleStripeSuccess = (paymentIntentId: string) => {
    navigate('/checkout/result?status=success', {
      state: { parentOrderId, paymentIntentId },
    });
  };

  if (!orderData) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20">
        <div className="bg-white border border-gray-100 rounded-2xl p-6 space-y-3">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-11 w-full rounded-xl" />
        </div>
      </div>
    );
  }

  const checkoutOrders = normalizedOrderData?.orders ?? [];
  const orderCode = normalizedOrderData?.orderCode ?? 'PENDING';
  const totalAmount = normalizedOrderData?.totalAmount ?? 0;
  const finalAmount = normalizedOrderData?.finalAmount ?? totalAmount;
  const clientSecretPanelState = getClientSecretPanelState({
    data: clientSecretData,
    error: clientSecretError,
    failureCount: clientSecretFailureCount,
    isFetching: secretFetching,
    isPending: secretPending,
  });
  const clientSecretErrorMessage = getClientSecretErrorMessage(clientSecretError);

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <CheckoutStepper currentStep="payment" className="mb-6" />

      <h1 className="text-2xl font-bold text-gray-900 mb-2">Xác nhận thanh toán</h1>
      <p className="text-sm text-gray-500 mb-6">
        Mã đơn: <span className="font-mono font-medium">{orderCode}</span>
        {' · '}
        <span className="text-amber-600 font-medium">Vui lòng thanh toán ngay để xác nhận đơn hàng</span>
      </p>

      {/* Shipping address */}
      {(parentOrder?.shippingAddress) && (
        <div className="mb-6 bg-white rounded-2xl border border-gray-100 p-5">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-bold text-gray-900 flex items-center gap-2">
                <span>📍</span> Địa chỉ giao hàng
              </h2>
              <p className="text-sm text-gray-700 mt-1">
                {parentOrder?.shippingAddress?.fullAddress}
              </p>
            </div>
            <button
              onClick={() => navigate('/checkout')}
              className="text-sm text-blue-600 hover:underline"
            >
              Thay đổi
            </button>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <div className="lg:col-span-3">
          {clientSecretPanelState === 'initializing' && (
            <div className="bg-white rounded-2xl border border-gray-100 p-6">
              <div className="flex items-center gap-3 mb-3">
                <Skeleton className="h-5 w-5 rounded-full" />
                <Skeleton className="h-5 w-64" />
              </div>
              <div className="space-y-2">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-5/6" />
              </div>
              <div className="mt-5 space-y-3">
                <Skeleton className="h-12 w-full rounded-xl" />
                <Skeleton className="h-12 w-full rounded-xl" />
                <Skeleton className="h-12 w-full rounded-xl" />
              </div>
            </div>
          )}
          {clientSecretPanelState === 'ready' && clientSecretData?.clientSecret && (
            <Elements stripe={getStripe()} options={{ clientSecret: clientSecretData.clientSecret }}>
              <PaymentForm
                amount={finalAmount}
                onSuccess={handleStripeSuccess}
              />
            </Elements>
          )}
          {clientSecretPanelState === 'failed' && (
            <div className="bg-white rounded-2xl border border-gray-100 p-6 text-center">
              <p className="text-gray-700 font-medium">Không thể khởi tạo cổng thanh toán.</p>
              <p className="text-sm text-gray-500 mt-1">Vui lòng thử lại sau ít phút.</p>
              {clientSecretErrorMessage && (
                <p className="text-xs text-gray-400 mt-3">{clientSecretErrorMessage}</p>
              )}
            </div>
          )}
        </div>

        <div className="lg:col-span-2">
          <div className="bg-white rounded-2xl border border-gray-100 p-6 sticky top-24">
            <h2 className="font-bold text-gray-900 mb-4">📋 Đơn hàng</h2>
            <div className="space-y-3 mb-4 pb-4 border-b border-gray-100">
              {checkoutOrders.length === 0 && (
                <div className="space-y-2">
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-3 w-1/2" />
                  <Skeleton className="h-5 w-24" />
                </div>
              )}
              {checkoutOrders.map(order => (
                <div key={order.orderId} className="text-sm">
                  <p className="font-medium text-gray-900">{order.sellerName}</p>
                  <p className="text-xs text-gray-500 font-mono">{order.orderCode}</p>
                  <p className="font-bold text-red-600 mt-1">{fmt(order.finalAmt)}</p>
                </div>
              ))}
            </div>
            <div className="space-y-2 text-sm mb-5">
              <div className="flex justify-between text-gray-600">
                <span>Tạm tính</span>
                <span>{fmt(totalAmount)}</span>
              </div>
              <div className="flex justify-between text-gray-600">
                <span>Phí ship</span>
                <span className="text-green-600">Miễn phí</span>
              </div>
              <div className="h-px bg-gray-100" />
              <div className="flex justify-between font-bold text-gray-900 text-base">
                <span>Tổng</span>
                <span className="text-red-600">{fmt(finalAmount)}</span>
              </div>
            </div>
            <div className="p-3 bg-blue-50 rounded-xl text-xs text-blue-700 mb-3">
              Thanh toán an toàn qua Stripe. Dữ liệu thẻ của bạn được mã hoá.
            </div>
            <button
              onClick={() => navigate('/cart')}
              className="w-full py-2.5 border border-gray-200 hover:border-gray-300 text-gray-500 text-sm rounded-xl transition-colors"
            >
              ← Quay lại giỏ hàng
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
