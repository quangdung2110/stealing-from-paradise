import { useQuery } from '@tanstack/react-query';
import { orderApi, type OrderStatus } from '@shared/api/order.api';
import { Skeleton } from '@shared/components/ui';

const SAGA_STEPS: Array<{
  status: OrderStatus;
  label: string;
  icon: string;
}> = [
  { status: 'PAID',      label: 'Đã thanh toán',   icon: '💳' },
  { status: 'SHIPPING',   label: 'Đang giao hàng',  icon: '🚚' },
  { status: 'DELIVERED',  label: 'Đã giao hàng',    icon: '📦' },
];

const SAGA_STEP_ORDER = SAGA_STEPS.map(s => s.status);

interface OrderSagaTrackerProps {
  parentOrderId: number;
}

export default function OrderSagaTracker({ parentOrderId }: OrderSagaTrackerProps) {
  const { data: parentOrder, isLoading } = useQuery({
    queryKey: ['parent-order-saga', parentOrderId],
    queryFn: () => orderApi.getParentOrder(parentOrderId).then(r => r.data.data ?? null),
    refetchInterval: (query) => {
      const po = query.state.data;
      if (!po) return 5000;
      // All delivered or returned → stop polling
      if (po.status === 'DELIVERED' || po.status === 'CANCELLED' || po.status === 'RETURNED') return false;
      return 3000;
    },
  });

  if (isLoading || !parentOrder) {
    return (
      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <div className="space-y-4">
          <Skeleton className="h-5 w-48" />
          <Skeleton className="h-3 w-full" />
          <Skeleton className="h-3 w-3/4" />
          <div className="grid grid-cols-3 gap-3 mt-4">
            <Skeleton className="h-16 rounded-xl" />
            <Skeleton className="h-16 rounded-xl" />
            <Skeleton className="h-16 rounded-xl" />
          </div>
        </div>
      </div>
    );
  }

  const subOrders = parentOrder.orders ?? [];
  if (subOrders.length === 0) return null;

  // Find the furthest-advanced step among all sub-orders
  const subStatuses = subOrders.map(o => o.status as OrderStatus);
  const maxStepIndex = Math.max(
    0,
    ...subStatuses.map(s => SAGA_STEP_ORDER.indexOf(s)).filter(i => i >= 0),
  );

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6">
      <div className="flex items-center gap-2 mb-4">
        <span className="text-lg">📋</span>
        <h3 className="font-bold text-gray-900">Tiến trình đơn hàng</h3>
      </div>

      {/* Stepper */}
      <div className="grid grid-cols-3 gap-2 sm:gap-4 mb-6">
        {SAGA_STEPS.map((step, i) => {
          const isComplete = i <= maxStepIndex;
          const isCurrent = i === maxStepIndex + 1;

          return (
            <div key={step.status} className="relative flex flex-col items-center text-center">
              {i > 0 && (
                <div
                  className={`absolute left-[-50%] top-5 h-0.5 w-full sm:top-6 ${
                    isComplete ? 'bg-green-500' : 'bg-gray-200'
                  }`}
                />
              )}
              <div
                className={`relative z-10 flex h-10 w-10 sm:h-12 sm:w-12 items-center justify-center rounded-full border-2 bg-white transition-colors ${
                  isComplete
                    ? 'border-green-500 bg-green-500 text-white'
                    : isCurrent
                      ? 'border-blue-500 bg-blue-50'
                      : 'border-gray-200 text-gray-400'
                }`}
              >
                {isComplete ? (
                  <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                  </svg>
                ) : (
                  <span className="text-lg">{step.icon}</span>
                )}
              </div>
              <span
                className={`mt-2 text-[11px] sm:text-sm font-semibold ${
                  isComplete || isCurrent ? 'text-gray-900' : 'text-gray-400'
                }`}
              >
                {step.label}
              </span>
            </div>
          );
        })}
      </div>

      {/* Per sub-order status */}
      <div className="space-y-2">
        {subOrders.map(order => {
          const stepIdx = SAGA_STEP_ORDER.indexOf(order.status as OrderStatus);
          const stepLabel = stepIdx >= 0 ? SAGA_STEPS[stepIdx].label : order.status;

          return (
            <div
              key={order.orderId}
              className="flex items-center justify-between bg-gray-50 rounded-xl px-4 py-3"
            >
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900 truncate">
                  {order.sellerName}
                </p>
                <p className="text-xs text-gray-500 font-mono">{order.orderCode}</p>
              </div>
              <span
                className={`shrink-0 ml-3 px-2.5 py-1 rounded-full text-xs font-medium ${
                  stepIdx >= 0
                    ? 'bg-green-100 text-green-700'
                    : order.status === 'CANCELLED'
                      ? 'bg-red-100 text-red-700'
                      : 'bg-gray-100 text-gray-600'
                }`}
              >
                {stepLabel}
              </span>
            </div>
          );
        })}
      </div>

      {parentOrder.status === 'DELIVERED' && (
        <div className="mt-4 p-3 bg-green-50 border border-green-200 rounded-xl text-center">
          <p className="text-green-700 font-bold text-sm">🎉 Tất cả đơn hàng đã được giao thành công!</p>
        </div>
      )}
    </div>
  );
}
