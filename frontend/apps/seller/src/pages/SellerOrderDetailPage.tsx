/**
 * SellerOrderDetailPage — full detail view for a single seller sub-order.
 *
 * Shows buyer, payment, line items and totals, and exposes the same
 * UC-driven actions as the orders list. Features an order timeline,
 * refund status panel, and shipping tracking info.
 */
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { orderApi, type OrderStatus } from '@shared/api/order.api';
import { refundApi, type RefundResponse } from '@shared/api/refund.api';
import { paymentApi } from '@shared/api/payment.api';
import { fmtVnd, fmtDateTime } from '@shared/utils/format';
import { Badge, Card, Skeleton, type BadgeProps } from '@shared/components/ui';
import { getStatusMeta } from '@/lib/orderStatus';
import { canShip, canCancel, canReturnToSender } from '@/lib/orderActions';
import TrackingModal from '@/components/Orders/TrackingModal';
import RTSModal from '@/components/Orders/RTSModal';
import CancelOrderModal from '@/components/Orders/CancelOrderModal';

/* ─── Order Timeline ─────────────────────────────────────── */

function OrderTimeline({ status, createdAt, trackingNumber, carrier }: {
  status: string; createdAt: string; trackingNumber?: string | null; carrier?: string | null;
}) {
  const steps: { key: string; label: string; done: boolean; active: boolean; }[] = [
    { key: 'PAID', label: 'Đã thanh toán', done: true, active: false },
    { key: 'SHIPPING', label: 'Đang giao', done: ['SHIPPING', 'DELIVERED', 'RETURNED'].includes(status), active: status === 'SHIPPING' },
    { key: 'DELIVERED', label: 'Đã giao', done: status === 'DELIVERED', active: status === 'RETURNED' ? false : status === 'DELIVERED' },
  ];

  // For cancelled/refunded, show a different timeline
  const isCancelled = status === 'CANCELLED' || status === 'REFUNDED' || status === 'PARTIALLY_REFUNDED';
  if (isCancelled) {
    return (
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <h2 className="font-bold text-gray-900 mb-4">📋 Trạng thái đơn hàng</h2>
        <div className="space-y-0">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-green-100 text-green-600 flex items-center justify-center text-sm font-bold shrink-0">✓</div>
            <div>
              <p className="text-sm font-medium text-green-700">Đã thanh toán</p>
              <p className="text-xs text-gray-400">{fmtDateTime(createdAt)}</p>
            </div>
          </div>
          <div className="w-px h-4 bg-gray-200 ml-4" />
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-red-100 text-red-600 flex items-center justify-center text-sm font-bold shrink-0">✕</div>
            <div>
              <p className="text-sm font-medium text-red-700">{status === 'REFUNDED' || status === 'PARTIALLY_REFUNDED' ? 'Đã hoàn tiền' : 'Đã huỷ'}</p>
              <p className="text-xs text-gray-400">{status}</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
      <h2 className="font-bold text-gray-900 mb-4">📋 Trạng thái đơn hàng</h2>
      <div className="space-y-0">
        {steps.map((step, i) => (
          <div key={step.key}>
            <div className="flex items-center gap-3">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold shrink-0 ${
                step.done ? 'bg-green-100 text-green-600' :
                step.active ? 'bg-blue-100 text-blue-600 ring-2 ring-blue-200' :
                'bg-gray-100 text-gray-400'
              }`}>
                {step.done ? '✓' : step.active ? '●' : '○'}
              </div>
              <div>
                <p className={`text-sm font-medium ${
                  step.done ? 'text-green-700' : step.active ? 'text-blue-700' : 'text-gray-400'
                }`}>
                  {step.label}
                </p>
                {step.active && trackingNumber && (
                  <p className="text-xs text-gray-500">Mã vận đơn: {trackingNumber}{carrier ? ` (${carrier})` : ''}</p>
                )}
              </div>
            </div>
            {i < steps.length - 1 && <div className="w-px h-5 bg-gray-200 ml-4" />}
          </div>
        ))}
        {status === 'RETURNED' && (
          <>
            <div className="w-px h-4 bg-gray-200 ml-4" />
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-full bg-orange-100 text-orange-600 flex items-center justify-center text-sm font-bold shrink-0">↩</div>
              <div>
                <p className="text-sm font-medium text-orange-700">Hoàn hàng</p>
                <p className="text-xs text-gray-400">Đã hoàn về người bán</p>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

/* ─── Refund Panel ────────────────────────────────────────── */

const REFUND_STATUS_META: Record<string, { label: string; tone: BadgeProps['tone'] }> = {
  PENDING: { label: 'Chờ duyệt', tone: 'warning' },
  APPROVED: { label: 'Đã duyệt', tone: 'info' },
  PROCESSING: { label: 'Đang xử lý', tone: 'info' },
  SUCCESS: { label: 'Đã hoàn', tone: 'success' },
  COMPLETED: { label: 'Hoàn tất', tone: 'success' },
  RTS_COMPLETED: { label: 'Hoàn hàng xong', tone: 'success' },
  REJECTED: { label: 'Từ chối', tone: 'danger' },
  FAILED: { label: 'Thất bại', tone: 'danger' },
};

function getRefundStatusMeta(status: string) {
  return REFUND_STATUS_META[status] ?? { label: status, tone: 'neutral' as const };
}

function RefundStatusPanel({ refunds, isLoading, isError }: {
  refunds: RefundResponse[]; isLoading: boolean; isError: boolean;
}) {
  if (isLoading) {
    return (
      <Card className="mb-6">
        <Skeleton className="h-5 w-40 mb-4" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
      </Card>
    );
  }
  if (isError) {
    return (
      <Card className="mb-6 border-amber-100 bg-amber-50">
        <h2 className="font-bold text-gray-900 mb-2">Yêu cầu hoàn tiền</h2>
        <p className="text-sm text-amber-700">Không thể tải trạng thái hoàn tiền của đơn này.</p>
      </Card>
    );
  }
  if (refunds.length === 0) {
    return (
      <Card className="mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="font-bold text-gray-900">Yêu cầu hoàn tiền</h2>
            <p className="text-sm text-gray-500">Đơn này chưa có yêu cầu hoàn tiền.</p>
          </div>
          <Badge tone="neutral">Chưa có refund</Badge>
        </div>
      </Card>
    );
  }
  return (
    <Card className="mb-6">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="font-bold text-gray-900">Yêu cầu hoàn tiền</h2>
          <p className="text-sm text-gray-500">{refunds.length} yêu cầu</p>
        </div>
        <Badge tone="info">{refunds.length} refund</Badge>
      </div>
      <div className="space-y-3">
        {refunds.map(refund => {
          const meta = getRefundStatusMeta(refund.status);
          const effectiveAmount = refund.adjustAmount ?? refund.amount;
          return (
            <div key={refund.refundId} className="rounded-xl border border-gray-100 bg-gray-50/60 p-4">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-gray-900">{refund.groupRef || `Refund #${refund.refundId}`}</span>
                    <Badge tone={meta.tone} dot>{meta.label}</Badge>
                    <Badge tone="neutral">{refund.type}</Badge>
                  </div>
                  <p className="mt-1 text-sm text-gray-600">{refund.reason}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-gray-500">Số tiền hoàn</p>
                  <p className="font-bold text-red-600">{fmtVnd(effectiveAmount)}</p>
                </div>
              </div>
              {refund.items && refund.items.length > 0 && (
                <div className="mb-3 space-y-2">
                  {refund.items.map((item, index) => (
                    <div key={`${refund.refundId}-${item.orderItemId}-${index}`} className="flex items-center justify-between gap-3 rounded-lg bg-white px-3 py-2 text-sm">
                      <div className="min-w-0">
                        <p className="truncate font-medium text-gray-800">{item.productName || `#${item.orderItemId}`}</p>
                        <p className="text-xs text-gray-500">x{item.quantity} · {item.itemReason || ''}</p>
                      </div>
                      <span className="shrink-0 font-semibold text-gray-900">{fmtVnd(item.refundAmount)}</span>
                    </div>
                  ))}
                </div>
              )}
              <div className="grid gap-2 text-xs text-gray-500 sm:grid-cols-2">
                <span>Tạo: {fmtDateTime(refund.createdAt)}</span>
                {refund.reviewedAt && <span>Duyệt: {fmtDateTime(refund.reviewedAt)}</span>}
                {refund.adminNote && <span className="sm:col-span-2">Ghi chú admin: {refund.adminNote}</span>}
                {refund.rejectReason && <span className="sm:col-span-2 text-red-600">Lý do từ chối: {refund.rejectReason}</span>}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

/* ─── Main Page ──────────────────────────────────────────── */

export default function SellerOrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const id = Number(orderId);
  const queryClient = useQueryClient();

  const [showTracking, setShowTracking] = useState(false);
  const [showReturn, setShowReturn] = useState(false);
  const [showCancel, setShowCancel] = useState(false);

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['seller-order', id],
    queryFn: () => orderApi.getOrderById(id).then(r => r.data.data),
    enabled: id > 0,
    retry: 1,
  });

  const { data: paymentData } = useQuery({
    queryKey: ['payment-for-seller', order?.parentOrderId],
    queryFn: () => paymentApi.getPayment(order!.parentOrderId!).then(r => r.data.data),
    enabled: !!order?.parentOrderId,
    retry: 1,
  });

  const { data: refunds = [], isLoading: refundsLoading, error: refundsError } = useQuery({
    queryKey: ['seller-order-refunds', id],
    queryFn: () => refundApi.getRefundsByOrder(id).then(r => r.data.data),
    enabled: id > 0 && !!order,
    retry: 1,
  });

  const onMutationSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['seller-order', id] });
    queryClient.invalidateQueries({ queryKey: ['seller-orders'] });
  };

  if (id <= 0) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <p className="text-gray-500">Vui lòng truy cập từ danh sách đơn hàng.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-8 space-y-6">
        <Card><Skeleton className="h-6 w-44 mb-4" /><Skeleton className="h-4 w-2/3" /></Card>
        <Card><Skeleton className="h-5 w-36 mb-4" /><Skeleton className="h-14 w-full rounded-xl" /></Card>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500 mb-4">Không tìm thấy đơn hàng.</p>
        <a href="/orders" className="text-blue-600 hover:underline">← Quay lại danh sách</a>
      </div>
    );
  }

  const st = getStatusMeta(order.status);

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <a href="/orders" className="text-gray-400 hover:text-gray-600 text-2xl leading-none">←</a>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Chi tiết đơn hàng</h1>
          <p className="text-sm text-gray-500 font-mono">{order.orderCode}</p>
        </div>
      </div>

      {/* Status + actions strip */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <div className="flex items-center gap-4 flex-wrap">
          <span className={`px-3 py-1.5 rounded-full text-sm font-semibold ${st.bg} ${st.color}`}>
            {st.label}
          </span>
          {order.isFlashSale && (
            <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-orange-100 text-orange-700">⚡ Flash Sale</span>
          )}
          <span className="text-sm text-gray-500">
            Đặt lúc {fmtDateTime(order.createdAt)}
          </span>
          <div className="flex-1" />
          <div className="flex gap-2">
            {canShip(order.status) && (
              <button onClick={() => setShowTracking(true)}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium transition-colors">
                📦 Cập nhật vận đơn
              </button>
            )}
            {canCancel(order.status, order.trackingNumber) && (
              <button onClick={() => setShowCancel(true)}
                className="px-4 py-2 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-xl text-sm font-medium transition-colors">
                Huỷ đơn
              </button>
            )}
            {canReturnToSender(order.status) && (
              <button onClick={() => setShowReturn(true)}
                className="px-4 py-2 bg-orange-50 hover:bg-orange-100 text-orange-600 border border-orange-200 rounded-xl text-sm font-medium transition-colors">
                ↩ Hoàn hàng
              </button>
            )}
          </div>
        </div>
        {order.trackingNumber && (
          <div className="mt-3 pt-3 border-t border-gray-100 flex items-center gap-4 text-sm">
            <span className="text-gray-500">Mã vận đơn:</span>
            <span className="font-mono font-medium text-gray-900">{order.trackingNumber}</span>
            {order.carrier && <span className="text-gray-400">({order.carrier})</span>}
          </div>
        )}
      </div>

      {/* Timeline */}
      <OrderTimeline
        status={order.status}
        createdAt={order.createdAt}
        trackingNumber={order.trackingNumber}
        carrier={order.carrier}
      />

      {/* Customer */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <h2 className="font-bold text-gray-900 mb-3">👤 Thông tin khách hàng</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-gray-500 text-xs">Tên</p>
            <p className="font-medium text-gray-900">{order.buyerName || `User #${order.buyerId}`}</p>
          </div>
          {order.shippingAddress && (
            <div className="col-span-2">
              <p className="text-gray-500 text-xs">Địa chỉ giao hàng</p>
              <p className="font-medium text-gray-900">{order.shippingAddress.fullAddress}</p>
            </div>
          )}
        </div>
      </div>

      {/* Payment info */}
      {paymentData && (
        <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
          <h2 className="font-bold text-gray-900 mb-3">💳 Thông tin thanh toán</h2>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-gray-500 text-xs">Số tiền</p>
              <p className="font-bold text-gray-900">{fmtVnd(paymentData.amount)}</p>
            </div>
            <div>
              <p className="text-gray-500 text-xs">Phương thức</p>
              <p className="font-medium text-gray-700">{paymentData.method}</p>
            </div>
            <div>
              <p className="text-gray-500 text-xs">Mã giao dịch</p>
              <p className="font-mono text-xs text-gray-600">{paymentData.transRef}</p>
            </div>
            {paymentData.paidAt && (
              <div>
                <p className="text-gray-500 text-xs">Thanh toán lúc</p>
                <p className="font-medium text-gray-700 text-xs">{fmtDateTime(paymentData.paidAt)}</p>
              </div>
            )}
          </div>
        </div>
      )}

      <RefundStatusPanel refunds={refunds} isLoading={refundsLoading} isError={!!refundsError} />

      {/* Line items */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden mb-6">
        <div className="px-5 py-4 border-b border-gray-50">
          <h2 className="font-bold text-gray-900">📦 Sản phẩm ({order.items?.length ?? 0})</h2>
        </div>
        {order.items?.map(item => (
          <div key={item.orderItemId} className="flex items-center gap-4 px-5 py-4 border-b border-gray-50 last:border-b-0">
            <div className="w-16 h-16 rounded-xl bg-gray-100 flex items-center justify-center text-2xl shrink-0 overflow-hidden">
              {item.imageSnapshot ? <img src={item.imageSnapshot} alt="" className="w-full h-full object-cover" /> : '📦'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="font-medium text-gray-900 truncate">{item.productName}</p>
              <p className="text-sm text-gray-500">{item.variantName}</p>
              <div className="flex items-center gap-2 mt-0.5">
                <span className="text-xs text-gray-400">SKU: {item.skuCode}</span>
                <span className="text-gray-300">·</span>
                <span className="text-xs text-gray-400">x{item.quantity}</span>
                {item.refundedQuantity > 0 && (
                  <><span className="text-gray-300">·</span><span className="text-xs text-blue-600">Đã hoàn: {item.refundedQuantity}</span></>
                )}
              </div>
            </div>
            <div className="text-right shrink-0">
              <p className="font-bold text-gray-900">{fmtVnd(item.priceSnapshot * item.quantity)}</p>
              <p className="text-xs text-gray-400">{fmtVnd(item.priceSnapshot)}/sp</p>
            </div>
          </div>
        ))}
      </div>

      {/* Totals */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <h2 className="font-bold text-gray-900 mb-3">💰 Tổng kết</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between text-gray-600">
            <span>Tổng tiền</span>
            <span>{fmtVnd(order.totalAmt)}</span>
          </div>
          <div className="flex justify-between font-bold text-base">
            <span>Thanh toán</span>
            <span className="text-red-600 text-lg">{fmtVnd(order.finalAmt)}</span>
          </div>
        </div>
      </div>

      {/* Action modals */}
      {showTracking && (
        <TrackingModal orderId={id} orderCode={order.orderCode}
          customerLabel={order.buyerName || `User #${order.buyerId}`}
          onClose={() => setShowTracking(false)} onSuccess={onMutationSuccess} />
      )}
      {showCancel && (
        <CancelOrderModal orderId={id} orderCode={order.orderCode}
          onClose={() => setShowCancel(false)} onSuccess={onMutationSuccess} />
      )}
      {showReturn && (
        <RTSModal orderId={id} orderCode={order.orderCode}
          onClose={() => setShowReturn(false)} onSuccess={onMutationSuccess} />
      )}
    </div>
  );
}
