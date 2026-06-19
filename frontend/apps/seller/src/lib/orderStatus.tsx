/**
 * Centralised order-status presentation.
 *
 * A single source of truth for the Vietnamese label and Tailwind chip colours
 * of each {@link OrderStatus}. Previously this map was duplicated (with subtle
 * differences) across SellerOrdersPage, SellerOrderDetailPage and OrderDrawer.
 */
import type { OrderStatus } from '@shared/api/order.api';

export interface StatusMeta {
  /** Vietnamese display label. */
  label: string;
  /** Tailwind background colour class for the chip. */
  bg: string;
  /** Tailwind text colour class for the chip. */
  color: string;
}

/** Label + chip styling for every order status. */
export const ORDER_STATUS_META: Record<OrderStatus, StatusMeta> = {
  PENDING:            { label: 'Chờ xác nhận',  bg: 'bg-yellow-100', color: 'text-yellow-700' },
  PAID:               { label: 'Đã thanh toán', bg: 'bg-blue-100',   color: 'text-blue-700' },
  SHIPPING:           { label: 'Đang giao',     bg: 'bg-purple-100', color: 'text-purple-700' },
  DELIVERED:          { label: 'Đã giao',       bg: 'bg-green-100',  color: 'text-green-700' },
  CANCELLED:          { label: 'Đã huỷ',        bg: 'bg-red-100',    color: 'text-red-700' },
  RETURNED:           { label: 'Hoàn hàng',     bg: 'bg-orange-100', color: 'text-orange-700' },
  PARTIALLY_REFUNDED: { label: 'Hoàn một phần', bg: 'bg-indigo-100', color: 'text-indigo-700' },
  REFUNDED:           { label: 'Đã hoàn',       bg: 'bg-gray-100',   color: 'text-gray-600' },
};

const FALLBACK: StatusMeta = { label: '—', bg: 'bg-gray-100', color: 'text-gray-700' };

/** Resolve status metadata, falling back gracefully for unknown values. */
export function getStatusMeta(status: string): StatusMeta {
  return ORDER_STATUS_META[status as OrderStatus] ?? { ...FALLBACK, label: status };
}

/** Reusable coloured status chip used by order list, detail and drawer views. */
export function OrderStatusBadge({ status, className = '' }: { status: string; className?: string }) {
  const meta = getStatusMeta(status);
  return (
    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${meta.bg} ${meta.color} ${className}`}>
      {meta.label}
    </span>
  );
}
