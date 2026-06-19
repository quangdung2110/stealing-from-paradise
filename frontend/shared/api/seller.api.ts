import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

/** Seller stats for dashboard */
export interface SellerDashboardStats {
  totalProducts: number;
  ordersToday: number;
  revenueMonth: number;
  pendingOrders: number;
  activeProducts: number;
}

/** Stripe onboarding status — matches StripeOnboardingStatusResponse from backend */
export interface StripeOnboardingStatus {
  stripeAccountId?: string;
  accountStatus?: string;
  detailsSubmitted?: boolean;
  chargesEnabled?: boolean;
  payoutsEnabled?: boolean;
  onboardingStatus?: 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'SUSPENDED';
  onboardingUrl?: string | null;
  expressDashboardUrl?: string | null;
}

/** Stripe dashboard link response */
export interface StripeDashboardLink {
  dashboardUrl: string;
  stripeAccountId: string;
  accountStatus: string;
}

/** Seller earnings summary */
export interface SellerEarnings {
  totalEarnings: number;
  availableBalance: number;
  pendingBalance: number;
  platformFeePercentage: number;
  totalOrders: number;
  transfers: SellerTransferItem[];
}

/** A single seller transfer / earnings record */
export interface SellerTransferItem {
  id: number;
  orderId: number;
  orderCode?: string;
  transferAmount: number;
  feeAmount: number;
  netAmount: number;
  stripeTransferId: string | null;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | 'REVERSED';
  createdAt: string;
  updatedAt: string;
}

/** Product variant response */
export interface SellerVariant {
  id: string;
  skuCode: string;
  variantName: string;
  price: number;
  stock: number;
  status?: string;
}

/** Seller product list item */
export interface SellerProduct {
  productId: string;
  name: string;
  category: string;
  categoryId?: string;
  categoryName?: string;
  categorySlug?: string;
  price: number;
  originalPrice?: number;
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'UNPUBLISHED' | 'PUBLISHED' | 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';
  stockAvailable: number;
  images?: string[];
  variantsCount: number;
  variants?: SellerVariant[];
  createdAt: string;
  updatedAt?: string;
}

interface RawSellerVariant {
  id: string;
  skuCode?: string;
  variantCode?: string;
  variantName?: string;
  price: number;
  stock?: number;
  stockQuantity?: number;
  status?: string;
}

interface ProductWriteInput {
  name: string;
  description: string;
  categoryId: string;
  attributes?: Record<string, unknown>;
  images?: string[];
}

interface VariantWriteInput {
  skuCode?: string;
  variantCode?: string;
  variantName?: string;
  price?: number;
  stock?: number;
  stockQuantity?: number;
  originalPrice?: number;
  status?: string;
  imageUrl?: string;
  version?: number;
}

function mapSellerVariant(raw: RawSellerVariant): SellerVariant {
  return {
    id: raw.id,
    skuCode: raw.skuCode ?? raw.variantCode ?? '',
    variantName: raw.variantName ?? raw.skuCode ?? raw.variantCode ?? '',
    price: raw.price,
    stock: raw.stock ?? raw.stockQuantity ?? 0,
    status: raw.status,
  };
}

function toProductWriteRequest(data: ProductWriteInput) {
  return {
    name: data.name,
    description: data.description,
    categoryId: data.categoryId,
    attributes: data.attributes,
  };
}

function toCreateVariantRequest(data: VariantWriteInput) {
  return {
    variantCode: data.variantCode ?? data.skuCode,
    variantName: data.variantName,
    price: data.price,
    originalPrice: data.originalPrice,
    stockQuantity: data.stockQuantity ?? data.stock,
    imageUrl: data.imageUrl,
  };
}

function toUpdateVariantRequest(data: VariantWriteInput) {
  return {
    variantName: data.variantName,
    price: data.price,
    originalPrice: data.originalPrice,
    stockQuantity: data.stockQuantity ?? data.stock,
    status: data.status,
    imageUrl: data.imageUrl,
    version: data.version,
  };
}

function mapVariantResponse<T extends { data: ApiResponse<RawSellerVariant> }>(res: T) {
  return {
    ...res,
    data: {
      ...res.data,
      data: res.data.data ? mapSellerVariant(res.data.data) : res.data.data,
    } as ApiResponse<SellerVariant>,
  };
}

export const sellerApi = {
  /** Seller dashboard stats */
  getDashboardStats: () =>
    apiClient.get<ApiResponse<SellerDashboardStats>>('/sellers/me/dashboard'),

  /** Start Stripe onboarding */
  startStripeOnboarding: () =>
    apiClient.post<ApiResponse<{ onboardingUrl: string; expressDashboardUrl: string; stripeAccountId: string; expiresAt: string }>>('/stripe/onboarding/start'),

  /** Get Stripe onboarding status */
  getStripeStatus: () =>
    apiClient.get<ApiResponse<StripeOnboardingStatus>>('/stripe/onboarding/status'),

  /** Refresh expired Stripe onboarding link */
  refreshStripeLink: () =>
    apiClient.post<ApiResponse<{ onboardingUrl: string; expressDashboardUrl: string; stripeAccountId: string; expiresAt: string }>>('/stripe/onboarding/refresh-link'),

  /** Submit a DRAFT product for review */
  submitForReview: (productId: string) =>
    apiClient.post<ApiResponse<SellerProduct>>(`/seller/products/${productId}/submit`),

  /** Publish an APPROVED product */
  publishProduct: (productId: string) =>
    apiClient.post<ApiResponse<SellerProduct>>(`/seller/products/${productId}/publish`),

  /** Unpublish a PUBLISHED/APPROVED product */
  unpublishProduct: (productId: string) =>
    apiClient.post<ApiResponse<SellerProduct>>(`/seller/products/${productId}/unpublish`),

  /** List variants for a product */
  getVariants: (productId: string) =>
    apiClient.get<ApiResponse<RawSellerVariant[]>>(`/seller/products/${productId}/variants`).then(res => ({
      ...res,
      data: {
        ...res.data,
        data: (res.data.data ?? []).map(mapSellerVariant),
      } as ApiResponse<SellerVariant[]>,
    })),

  /** Create a variant */
  createVariant: (productId: string, data: { skuCode: string; variantName: string; price: number; stock: number }) =>
    apiClient
      .post<ApiResponse<RawSellerVariant>>(`/seller/products/${productId}/variants`, toCreateVariantRequest(data))
      .then(mapVariantResponse),

  /** Update a variant */
  updateVariant: (variantId: string, data: { skuCode?: string; variantName?: string; price?: number; stock?: number }) =>
    apiClient
      .put<ApiResponse<RawSellerVariant>>(`/seller/variants/${variantId}`, toUpdateVariantRequest(data))
      .then(mapVariantResponse),

  /** Delete a variant */
  deleteVariant: (variantId: string) =>
    apiClient.delete<ApiResponse<void>>(`/seller/variants/${variantId}`),

  /** Get presigned URL for image upload */
  getPresignedUrl: (productId: string, fileName: string, contentType: string) =>
    apiClient.get<ApiResponse<PresignedUrlResponse>>(`/products/${productId}/presigned-url`, {
      params: { filename: fileName, contentType },
    }),

  /** Get seller earnings summary with all transfer records */
  getEarnings: () =>
    apiClient.get<ApiResponse<SellerEarnings>>('/seller/payments/earnings'),

  /** Get Stripe Dashboard single-use login link (valid 30 min) */
  getStripeDashboardLink: () =>
    apiClient.get<ApiResponse<StripeDashboardLink>>('/seller/payments/stripe-dashboard'),

  /** Create a new product */
  createProduct: (data: ProductWriteInput) =>
    apiClient.post<ApiResponse<SellerProduct>>('/products', toProductWriteRequest(data)),

  /** Delete a product (seller owner) */
  deleteProduct: (productId: string) =>
    apiClient.delete<ApiResponse<void>>(`/seller/products/${productId}`),

  /** Update a product */
  updateProduct: (productId: string, data: ProductWriteInput) =>
    apiClient.put<ApiResponse<SellerProduct>>(`/products/${productId}`, toProductWriteRequest(data)),

  // ─── Inventory ──────────────────────────────────────────────────────────────

  /** Adjust inventory by SKU */
  adjustInventory: (data: { skuCode: string; delta: number; reason: string }) =>
    apiClient.post<ApiResponse<InventoryResponse>>('/seller/inventory/adjust', {
      delta: data.delta,
      reason: 'MANUAL',
    }, {
      params: { skuCode: data.skuCode },
    }),

  /** Get inventory adjustment logs for a SKU */
  getInventoryLogs: (skuCode: string) =>
    apiClient.get<ApiResponse<InventoryLogEntry[]>>(`/seller/inventory/${skuCode}/logs`),

  /** Restock inventory by SKU */
  restockInventory: (skuCode: string, data: { quantity: number; reason: string; note?: string }) =>
    apiClient.put<ApiResponse<InventoryResponse>>(`/inventory/${skuCode}/restock`, { quantity: data.quantity }),
};

/** Inventory response */
export interface InventoryResponse {
  variantId: string;
  variantCode: string;
  stockTotal: number;
  stockLocked: number;
  stockAvailable: number;
  stockFlashReserved: number;
}

/** Inventory log entry */
export interface InventoryLogEntry {
  logId: number;
  skuCode: string;
  delta: number;
  stockBefore: number;
  stockAfter: number;
  reason: string;
  note?: string;
  changedBy: string;
  createdAt: string;
}

/** Presigned URL response from MinIO */
export interface PresignedUrlResponse {
  presignedUrl: string;
  objectUrl: string;
  expiresIn: number;
}
