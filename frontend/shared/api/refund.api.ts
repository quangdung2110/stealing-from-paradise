import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

// ─── Refund Types ────────────────────────────────────────────────────────────

export interface RefundItemRequest {
  orderItemId: number;
  quantity: number;
  itemReason?: string;
}

export interface FullRefundRequest {
  reason: string;
  evidenceImages?: string[];
}

export interface PartialRefundRequest {
  reason: string;
  items: RefundItemRequest[];
  evidenceImages?: string[];
}

export interface RefundItemResponse {
  orderItemId: number;
  itemId?: number;
  productName?: string;
  imageSnapshot?: string;
  quantity: number;
  refundAmount: number;
  itemReason?: string;
  status: string;
  trackingNumber?: string;
  returnTrackingNumber?: string;
  returnedAt?: string;
}

export interface RefundResponse {
  refundId: number;
  orderId: number;
  groupRef: string;
  type: string;
  status: string;
  amount: number;
  reason: string;
  initiatedBy: string;
  refundReasonType?: string;
  evidenceImages?: string[];
  adminNote?: string;
  rejectReason?: string;
  adjustAmount?: number;
  reviewedBy?: number;
  reviewedAt?: string;
  stripeRefundId?: string;
  items?: RefundItemResponse[];
  createdAt: string;
  updatedAt?: string;
}

export interface RefundReturnEvidence {
  type: string;
  trackingNumber?: string;
  recordedAt?: string;
}

export interface RefundDetailResponse extends RefundResponse {
  refundCode?: string;
  causedBy?: 'SELLER' | 'BUYER' | string;
  trackingNumber?: string;
  returnEvidence?: RefundReturnEvidence[];
}

type RefundPageResponse = PageResponse<RefundResponse>;

function normalizeRefundPage(
  payload: RefundPageResponse | RefundResponse[] | undefined,
  page = 0,
  size = 20,
): RefundPageResponse {
  if (Array.isArray(payload)) {
    return {
      content: payload,
      page,
      size,
      totalElements: payload.length,
      totalPages: payload.length > 0 ? 1 : 0,
      last: true,
    };
  }

  return {
    content: payload?.content ?? [],
    page: payload?.page ?? page,
    size: payload?.size ?? size,
    totalElements: payload?.totalElements ?? 0,
    totalPages: payload?.totalPages ?? 0,
    last: payload?.last ?? true,
  };
}

export interface FullRefundCreatedResponse {
  groupRef: string;
  type: string;
  totalAmount: number;
  status: string;
  refunds: {
    refundId: number;
    orderId: number;
    sellerId: number;
    amount: number;
    itemCount: number;
  }[];
  estimatedDays: number;
}

export interface FullRefundStatus {
  groupRef: string;
  type: string;
  overallStatus: string;
  totalAmount: number;
  refunds: {
    refundId: number;
    orderId: number;
    status: string;
    refundRef?: string;
  }[];
}

export interface RefundPresignedUrlResponse {
  url?: string;
  presignedUrl?: string;
  presigned_url?: string;
  uploadUrl?: string;
  upload_url?: string;
  objectUrl?: string;
  object_url?: string;
  cdnUrl?: string;
  cdn_url?: string;
  fileName?: string;
  file_name?: string;
  contentType?: string;
  content_type?: string;
  expiresAt?: string;
  expires_at?: string;
}

// ─── Admin Refund Types ──────────────────────────────────────────────────────

export interface AdminRefundApproveRequest {
  adminNote: string;
  adjustAmount?: number;
  causedBy?: 'SELLER' | 'BUYER';
  trackingNumber?: string;
}

export interface AdminRefundRejectRequest {
  rejectReason: string;
  fraudEvidence?: boolean;
}

export interface AdminRefundApproveResponse {
  refundId: number;
  status: string;
  amount: number;
  trackingNumber?: string;
  reviewedBy: string;
  reviewedAt: string;
}

// ─── Admin Refund API ────────────────────────────────────────────────────────

export const adminRefundApi = {
  /** List all refunds with filters */
  list: (params?: {
    status?: string;
    type?: string;
    sellerId?: number;
    groupRef?: string;
    fromDate?: string;
    toDate?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<RefundPageResponse>>('/admin/refunds', { params }),

  /** Get refund detail */
  getById: (refundId: number) =>
    apiClient.get<ApiResponse<RefundDetailResponse>>(`/admin/refunds/${refundId}`),

  /** Approve a refund */
  approve: (refundId: number, body: AdminRefundApproveRequest) =>
    apiClient.post<ApiResponse<AdminRefundApproveResponse>>(`/admin/refunds/${refundId}/approve`, body),

  /** Reject a refund */
  reject: (refundId: number, body: AdminRefundRejectRequest) =>
    apiClient.post<ApiResponse<{ refund_id: number; status: string }>>(`/admin/refunds/${refundId}/reject`, body),
};

// ─── Order-level Refund API ──────────────────────────────────────────────────

export const refundApi = {
  /** Full refund: all sub-orders in a parent order */
  requestFullRefund: (parentOrderId: number, body: FullRefundRequest) =>
    apiClient.post<ApiResponse<FullRefundCreatedResponse>>(`/orders/parent/${parentOrderId}/refund`, body),

  /** Full refund status check */
  getFullRefundStatus: (parentOrderId: number) =>
    apiClient.get<ApiResponse<FullRefundStatus>>(`/orders/parent/${parentOrderId}/refund`),

  /** Partial refund: items in ONE sub-order */
  requestPartialRefund: (orderId: number, body: PartialRefundRequest) =>
    apiClient.post<ApiResponse<RefundResponse>>(`/orders/${orderId}/refunds`, body),

  /** Partial refund across MULTIPLE sub-orders (different sellers) */
  requestMultiPartialRefund: (parentOrderId: number, body: PartialRefundRequest) =>
    apiClient.post<ApiResponse<FullRefundCreatedResponse>>(`/orders/parent/${parentOrderId}/refunds/partial`, body),

  /** Get refund history for ONE sub-order */
  getRefundsByOrder: (orderId: number) =>
    apiClient.get<ApiResponse<RefundResponse[]>>(`/orders/${orderId}/refunds`),

  /** Get specific refund within an order */
  getRefundById: (orderId: number, refundId: number) =>
    apiClient.get<ApiResponse<RefundResponse>>(`/orders/${orderId}/refunds/${refundId}`),

  /** List all buyer's refunds */
  getMyRefunds: (params?: { status?: string; page?: number; size?: number }) =>
    apiClient
      .get<ApiResponse<RefundPageResponse | RefundResponse[]>>('/orders/refunds', { params })
      .then(res => ({
        ...res,
        data: {
          ...res.data,
          data: normalizeRefundPage(res.data.data, params?.page ?? 0, params?.size ?? 20),
        } as ApiResponse<RefundPageResponse>,
      })),

  /** Get presigned URL for refund evidence upload */
  getRefundPresignedUrl: (orderId: number, fileName: string, contentType: string) =>
    apiClient.get<ApiResponse<RefundPresignedUrlResponse>>(
      `/orders/${orderId}/refunds/presigned-url`,
      { params: { file_name: fileName, content_type: contentType } }
    ),
};
