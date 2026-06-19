import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

// ─── Order Status ────────────────────────────────────────────────────────────
export type OrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'RETURNED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

// ─── Order Item ───────────────────────────────────────────────────────────────
export interface OrderItem {
  orderItemId: number;
  skuCode: string;
  productName: string;
  variantName: string;
  imageSnapshot?: string;
  priceSnapshot: number;
  quantity: number;
  refundedQuantity: number;
  fsItemId?: number | null;
}

// ─── Shipping Address ─────────────────────────────────────────────────────────
export interface ShippingAddress {
  fullAddress: string;
  provinceId: number;
  districtId: number;
}

// ─── Order (sub-order) ───────────────────────────────────────────────────────
export interface Order {
  orderId: number;
  parentOrderId: number;
  orderCode: string;
  sellerId: number;
  sellerName: string;
  buyerId?: number;
  buyerName?: string;
  status: OrderStatus;
  totalAmt: number;
  finalAmt: number;
  isFlashSale?: boolean;
  itemCount?: number;
  cancelledBy?: string | null;
  cancelReason?: string | null;
  shippingAddress?: ShippingAddress;
  trackingNumber?: string | null;
  carrier?: string | null;
  shippingDeadline?: string | null;
  returnTrackingNumber?: string | null;
  items?: OrderItem[];
  createdAt: string;
  updatedAt?: string;
}

// ─── Checkout ─────────────────────────────────────────────────────────────────
export interface CheckoutSubOrder {
  orderId: number;
  orderCode: string;
  sellerId: number;
  sellerName: string;
  totalAmt: number;
  finalAmt: number;
  status: string;
  itemCount: number;
  createdAt: string;
}

export interface CheckoutResponse {
  parentOrderId: number;
  orderCode: string;
  orders: CheckoutSubOrder[];
  totalAmount: number;
  finalAmount: number;
  itemsCount: number;
  paymentStatus?: string;
  timeoutAt?: string;
  createdAt: string;
}

export interface CheckoutRequest {
  addressId: number;
  itemIds: string[];
}

// ─── Parent Order ──────────────────────────────────────────────────────────────
export interface ParentOrderDetail {
  parentOrderId: number;
  orderCode: string;
  status: string;
  totalAmt: number;
  finalAmt: number;
  shippingAddress?: ShippingAddress;
  orders: Order[];
  createdAt: string;
  updatedAt?: string;
}

// ─── Cancel ───────────────────────────────────────────────────────────────────
export interface CancelOrderRequest {
  reason: string;
  note?: string;
}

export interface CancelOrderResponse {
  orderId: number;
  orderCode: string;
  status: string;
  cancelledBy: string;
  cancelledAt: string;
}

// ─── Tracking ─────────────────────────────────────────────────────────────────
export interface UpdateTrackingRequest {
  trackingNumber: string;
  carrier?: string;
  note?: string;
}

export interface TrackingUpdateResponse {
  orderId: number;
  orderCode: string;
  status: string;
  trackingNumber: string;
  carrier: string;
  shippingDeadline: string;
}

// ─── Order Summary (paginated list) ─────────────────────────────────────────
export interface OrderSummary {
  orderId: number;
  parentOrderId: number;
  orderCode: string;
  sellerId: number;
  sellerName?: string;
  status: OrderStatus;
  totalAmt: number;
  finalAmt: number;
  isFlashSale?: boolean;
  itemCount: number;
  items?: Pick<OrderItem, 'productName' | 'imageSnapshot' | 'quantity'>[];
  createdAt: string;
  updatedAt?: string;
}

// ─── Seller Order (with buyer info) ─────────────────────────────────────────
export interface SellerOrderItem {
  orderItemId: number;
  skuCode: string;
  productName: string;
  variantName: string;
  imageSnapshot?: string;
  priceSnapshot: number;
  quantity: number;
  refundedQuantity: number;
}

export interface SellerOrderSummary {
  orderId: number;
  parentOrderId: number;
  orderCode: string;
  buyerId: number;
  buyerName?: string;
  buyerUsername?: string;
  status: OrderStatus;
  totalAmt: number;
  finalAmt: number;
  isFlashSale?: boolean;
  itemCount: number;
  shippingAddress?: ShippingAddress;
  trackingNumber?: string | null;
  carrier?: string | null;
  createdAt: string;
  updatedAt?: string;
}

// ─── API ─────────────────────────────────────────────────────────────────────
export const orderApi = {
  /** Create order from cart (BUYER) */
  checkout: (data: CheckoutRequest) =>
    apiClient.post<ApiResponse<CheckoutResponse>>('/orders/checkout', data),

  /** List buyer's orders */
  getOrders: (params?: {
    status?: OrderStatus;
    from_date?: string;
    to_date?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<PageResponse<OrderSummary>>>('/orders', { params }),

  /** Get sub-order detail */
  getOrderById: (orderId: number) =>
    apiClient.get<ApiResponse<Order>>(`/orders/${orderId}`),

  /** Get parent order with all sub-orders */
  getParentOrder: (parentOrderId: number) =>
    apiClient.get<ApiResponse<ParentOrderDetail>>(`/orders/parent/${parentOrderId}`),

  /** Cancel order (BUYER or SELLER) */
  cancelOrder: (orderId: number, body: CancelOrderRequest) =>
    apiClient.post<ApiResponse<CancelOrderResponse>>(`/orders/${orderId}/cancel`, body),

  /** Update tracking number (SELLER) */
  updateTracking: (orderId: number, body: UpdateTrackingRequest) =>
    apiClient.put<ApiResponse<TrackingUpdateResponse>>(`/orders/${orderId}/tracking`, body),

  /** Confirm receipt (BUYER) */
  confirmReceived: (orderId: number) =>
    apiClient.post<ApiResponse<{ orderId: number; status: string }>>(`/orders/${orderId}/confirm-received`),

  /** Return to sender (SELLER) */
  returnToSender: (orderId: number, formData: FormData) =>
    apiClient.post<ApiResponse<{
      orderId: number;
      orderCode: string;
      orderStatus: string;
      refundId: number;
      refundStatus: string;
      refundAmount: number;
    }>>(`/orders/${orderId}/return-to-sender`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),

  /** List seller's orders */
  getSellerOrders: (params?: {
    status?: OrderStatus;
    from_date?: string;
    to_date?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<PageResponse<SellerOrderSummary>>>('/sellers/me/orders', { params }),
};
