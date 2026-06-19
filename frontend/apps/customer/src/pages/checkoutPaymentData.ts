import type { CheckoutPreviewResponse, CheckoutSubmitResponse } from '@shared/api/cart.api';
import type { CheckoutResponse, CheckoutSubOrder, ParentOrderDetail } from '@shared/api/order.api';

export type CheckoutPaymentData = Partial<CheckoutResponse> & {
  sessionId?: string;
  message?: string;
};

export const PAYMENT_DEADLINE_MS = 5 * 60 * 1000;

function asNumber(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

export function getPaymentDeadlineAt(createdAt?: string): string | undefined {
  if (!createdAt) return undefined;
  const created = new Date(createdAt).getTime();
  if (!Number.isFinite(created)) return undefined;
  return new Date(created + PAYMENT_DEADLINE_MS).toISOString();
}

export function getPaymentRemainingSeconds(createdAt?: string, nowMs = Date.now()): number | null {
  const deadlineAt = getPaymentDeadlineAt(createdAt);
  if (!deadlineAt) return null;
  const deadlineMs = new Date(deadlineAt).getTime();
  if (!Number.isFinite(deadlineMs)) return null;
  return Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000));
}

export function formatPaymentCountdown(seconds: number | null | undefined): string {
  if (seconds == null) return 'Đang đồng bộ';
  const remaining = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(remaining / 3600);
  const minutes = Math.floor((remaining % 3600) / 60);
  const secs = remaining % 60;

  if (hours > 0) {
    return `${hours} giờ ${minutes.toString().padStart(2, '0')} phút`;
  }
  if (minutes > 0) {
    return `${minutes} phút ${secs.toString().padStart(2, '0')} giây`;
  }
  return `${secs} giây`;
}

function parentOrders(parentOrder?: ParentOrderDetail | null): CheckoutSubOrder[] {
  return (parentOrder?.orders ?? []).map(order => ({
    orderId: order.orderId,
    orderCode: order.orderCode,
    sellerId: order.sellerId,
    sellerName: order.sellerName,
    totalAmt: asNumber(order.totalAmt),
    finalAmt: asNumber(order.finalAmt),
    status: order.status,
    itemCount: order.itemCount ?? order.items?.reduce((sum, item) => sum + item.quantity, 0) ?? 0,
    createdAt: order.createdAt,
  }));
}

function previewOrders(preview: CheckoutPreviewResponse | null | undefined, createdAt: string): CheckoutSubOrder[] {
  return (preview?.sellers ?? []).map((seller, index) => ({
    orderId: seller.sellerId || index + 1,
    orderCode: `SELLER-${seller.sellerId || index + 1}`,
    sellerId: seller.sellerId,
    sellerName: seller.sellerName || `Seller #${seller.sellerId}`,
    totalAmt: asNumber(seller.subtotal),
    finalAmt: asNumber(seller.subtotal),
    status: 'PENDING',
    itemCount: seller.items.reduce((sum, item) => sum + item.quantity, 0),
    createdAt,
  }));
}

export function buildCheckoutPaymentData(
  submit: CheckoutSubmitResponse,
  preview: CheckoutPreviewResponse | null | undefined,
  parentOrder?: ParentOrderDetail | null,
): CheckoutPaymentData {
  const createdAt = parentOrder?.createdAt ?? submit.createdAt ?? new Date().toISOString();
  const totalAmount = asNumber(parentOrder?.totalAmt, asNumber(submit.totalAmount, asNumber(preview?.totalAmount)));
  const finalAmount = asNumber(parentOrder?.finalAmt, totalAmount);

  return {
    sessionId: submit.sessionId,
    parentOrderId: parentOrder?.parentOrderId ?? submit.parentOrderId,
    orderCode: parentOrder?.orderCode ?? (parentOrder?.parentOrderId ? `PO-${parentOrder.parentOrderId}` : submit.sessionId),
    orders: parentOrders(parentOrder).length > 0 ? parentOrders(parentOrder) : previewOrders(preview, createdAt),
    totalAmount,
    finalAmount,
    itemsCount: parentOrder?.orders?.reduce((sum, order) => sum + (order.itemCount ?? 0), 0)
      ?? submit.totalItems
      ?? preview?.totalItems
      ?? 0,
    paymentStatus: 'PENDING',
    timeoutAt: getPaymentDeadlineAt(createdAt),
    createdAt,
    message: submit.message,
  };
}

export function normalizeCheckoutPaymentData(
  data: CheckoutPaymentData | null | undefined,
  parentOrder?: ParentOrderDetail | null,
): CheckoutPaymentData | null {
  if (!data && !parentOrder) return null;

  const createdAt = parentOrder?.createdAt ?? data?.createdAt ?? new Date().toISOString();
  const existingOrders = Array.isArray(data?.orders) ? data.orders : [];
  const totalAmount = asNumber(parentOrder?.totalAmt, asNumber(data?.totalAmount, asNumber(data?.finalAmount)));
  const finalAmount = asNumber(parentOrder?.finalAmt, asNumber(data?.finalAmount, totalAmount));

  return {
    ...data,
    parentOrderId: parentOrder?.parentOrderId ?? data?.parentOrderId,
    orderCode: parentOrder?.orderCode ?? data?.orderCode ?? data?.sessionId ?? 'PENDING',
    orders: parentOrders(parentOrder).length > 0 ? parentOrders(parentOrder) : existingOrders,
    totalAmount,
    finalAmount,
    itemsCount: parentOrder?.orders?.reduce((sum, order) => sum + (order.itemCount ?? 0), 0)
      ?? data?.itemsCount
      ?? existingOrders.reduce((sum, order) => sum + (order.itemCount ?? 0), 0),
    timeoutAt: data?.timeoutAt ?? getPaymentDeadlineAt(createdAt),
    createdAt,
  };
}
