import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface CartItem {
  /** Opaque item key "customerId:variantId" — the format checkout preview expects */
  cartItemId: string;
  variantId: string;
  skuCode: string;
  productId?: string;
  productName: string;
  variantName: string;
  image?: string;
  unitPrice: number;
  quantity: number;
  stockAvailable: number;
  isFlash: boolean;
  fsItemId?: number | null;
  flashPrice?: number | null;
  flashExpiresAt?: string | null;
  maxQuantityPerUser?: number | null;
  subtotal?: number;
  addedAt?: string;
  /** Price when item was added to cart — for detecting price changes */
  priceSnapshot?: number;
  /** Whether price changed since item was added to cart */
  priceChanged?: boolean;
}

export interface CartSeller {
  sellerId: number;
  sellerName: string;
  items: CartItem[];
  sellerSubtotal?: number;
}

export interface Cart {
  cartId?: string;
  userId?: number;
  sellers: CartSeller[];
  totalItems: number;
  subtotal: number;
  hasPriceChanges?: boolean;
}

// ─── Backend product-service cart shapes ───────────────────────────────────────

interface RawCartItemResponse {
  variantId: string;
  variantCode?: string | null;
  variantName?: string | null;
  productName?: string | null;
  priceSnapshot: number;
  currentPrice?: number | null;
  priceChanged?: boolean;
  quantity: number;
  variantImageSnapshot?: string | null;
  subtotal?: number;
  outOfStock?: boolean;
  unavailable?: boolean;
  insufficientStock?: boolean;
  stockAvailable?: number | null;
  sellerId?: number | null;
}

interface RawCartResponse {
  customerId: number;
  items: RawCartItemResponse[];
  totalItems: number;
  subtotal: number;
  hasPriceChanges?: boolean;
  groupedBySeller?: Record<string, RawCartItemResponse[]>;
}

function mapCartItem(raw: RawCartItemResponse, customerId: number): CartItem {
  return {
    cartItemId: `${customerId}:${raw.variantId}`,
    variantId: raw.variantId,
    skuCode: raw.variantCode ?? '',
    productName: raw.productName ?? raw.variantName ?? '',
    variantName: raw.variantName ?? '',
    image: raw.variantImageSnapshot ?? undefined,
    unitPrice: raw.currentPrice ?? raw.priceSnapshot,
    quantity: raw.quantity,
    stockAvailable: raw.unavailable
      ? 0
      : raw.stockAvailable ?? (raw.outOfStock ? 0 : raw.quantity),
    isFlash: false,
    subtotal: raw.subtotal,
    priceSnapshot: raw.priceSnapshot,
    priceChanged: raw.priceChanged ?? false,
  };
}

function mapCart(raw: RawCartResponse): Cart {
  const grouped = raw.groupedBySeller && Object.keys(raw.groupedBySeller).length > 0
    ? raw.groupedBySeller
    : { 0: raw.items ?? [] };
  const sellers: CartSeller[] = Object.entries(grouped).map(([sellerId, items]) => ({
    sellerId: Number(sellerId),
    sellerName: `Người bán #${sellerId}`,
    items: (items ?? []).map(i => mapCartItem(i, raw.customerId)),
    sellerSubtotal: (items ?? []).reduce((sum, i) => sum + (i.subtotal ?? 0), 0),
  }));
  return {
    userId: raw.customerId,
    sellers,
    totalItems: raw.totalItems ?? 0,
    subtotal: raw.subtotal ?? 0,
    hasPriceChanges: raw.hasPriceChanges,
  };
}

/** Extract the variant UUID from an opaque "customerId:variantId" item key */
const variantIdOf = (itemId: string) => itemId.includes(':') ? itemId.split(':')[1] : itemId;

// ─── Checkout Preview / Submit ──────────────────────────────────────────────────

export interface CartChangeDetail {
  variantId: string;
  skuCode?: string;
  productName?: string;
  reason: 'PRICE_CHANGED' | 'OUT_OF_STOCK' | 'INSUFFICIENT_STOCK' | 'VARIANT_UNAVAILABLE' | 'VARIANT_INACTIVE';
  currentValue: string;
  expectedValue: string;
}

export interface CartChangeError {
  error: string;
  message: string;
  details: CartChangeDetail[];
}

export interface CheckoutPreviewSellerGroup {
  sellerId: number;
  sellerName?: string;
  items: CheckoutPreviewItem[];
  subtotal: number;
}

export interface CheckoutPreviewItem {
  variantId: string;
  skuCode: string;
  productName: string;
  variantName: string;
  priceSnapshot: number;
  quantity: number;
  imageUrl?: string;
  subtotal: number;
  fsItemId?: number | null;
  sellerId: number;
}

export interface CheckoutPreviewResponse {
  previewToken: string;
  expiresAt: string;
  customerId: number;
  sellers: CheckoutPreviewSellerGroup[];
  totalItems: number;
  totalAmount: number;
  allValid: boolean;
}

export interface CheckoutSubmitResponse {
  sessionId: string;
  parentOrderId?: number;
  createdAt: string;
  totalItems: number;
  totalAmount: number;
  message: string;
}

export const cartApi = {
  getCart: () =>
    apiClient.get<ApiResponse<RawCartResponse>>('/cart').then(res => ({
      ...res,
      data: {
        ...res.data,
        data: res.data.data ? mapCart(res.data.data) : undefined,
      } as ApiResponse<Cart>,
    })),

  // Add item to cart — accepts the variant UUID or a SKU code
  addItem: (variantIdOrSku: string, quantity: number, _fsItemId?: number) => {
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(variantIdOrSku);
    return apiClient.post<ApiResponse<unknown>>('/cart/items', {
      ...(isUuid ? { variantId: variantIdOrSku } : { skuCode: variantIdOrSku }),
      quantity,
    });
  },

  // Update item quantity (itemId is the "customerId:variantId" key)
  updateItemQuantity: (itemId: string, quantity: number) =>
    apiClient.put<ApiResponse<unknown>>(`/cart/items/${variantIdOf(itemId)}`, {
      quantity,
    }),

  // Remove item from cart
  removeItem: (itemId: string) =>
    apiClient.delete<ApiResponse<void>>(`/cart/items/${variantIdOf(itemId)}`),

  clearCart: () =>
    apiClient.delete<ApiResponse<void>>('/cart'),

  checkoutPreview: (itemIds: string[]) =>
    apiClient.post<ApiResponse<CheckoutPreviewResponse>>('/cart/checkout/preview', { itemIds }),

  checkoutSubmit: (previewToken: string, addressId: number) =>
    apiClient.post<ApiResponse<CheckoutSubmitResponse>>('/cart/checkout/submit', {
      previewToken,
      addressId,
    }),
};
