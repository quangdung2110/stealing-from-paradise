/**
 * Centralised product-status presentation (label + chip styles), mirroring
 * {@link ./orderStatus}. Single source of truth for the badge previously inlined
 * in ProductManagementPage.
 */
import type { ProductStatus } from './productActions';

export interface ProductStatusMeta {
  label: string;
  bg: string;
  color: string;
}

export const PRODUCT_STATUS_META: Record<ProductStatus, ProductStatusMeta> = {
  DRAFT:       { label: 'Nháp',      bg: 'bg-gray-100',   color: 'text-gray-600' },
  PENDING:     { label: 'Chờ duyệt', bg: 'bg-yellow-100', color: 'text-yellow-700' },
  APPROVED:    { label: 'Đã duyệt',  bg: 'bg-green-100',  color: 'text-green-700' },
  REJECTED:    { label: 'Từ chối',   bg: 'bg-red-100',    color: 'text-red-700' },
  UNPUBLISHED: { label: 'Đã ẩn',     bg: 'bg-blue-100',   color: 'text-blue-700' },
  PUBLISHED:   { label: 'Đang bán',  bg: 'bg-green-100',  color: 'text-green-700' },
  ACTIVE:      { label: 'Đang bán',  bg: 'bg-green-100',  color: 'text-green-700' },
  INACTIVE:    { label: 'Đã ẩn',     bg: 'bg-blue-100',   color: 'text-blue-700' },
  OUT_OF_STOCK:{ label: 'Hết hàng',  bg: 'bg-orange-100', color: 'text-orange-700' },
};

const FALLBACK: ProductStatusMeta = { label: '—', bg: 'bg-gray-100', color: 'text-gray-600' };

export function getProductStatusMeta(status: string): ProductStatusMeta {
  return PRODUCT_STATUS_META[status as ProductStatus] ?? { ...FALLBACK, label: status };
}

/** Reusable product-status chip. */
export function ProductStatusBadge({ status }: { status: string }) {
  const m = getProductStatusMeta(status);
  return <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${m.bg} ${m.color}`}>{m.label}</span>;
}
