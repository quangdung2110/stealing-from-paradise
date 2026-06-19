import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

/** Admin: pending product for moderation */
export interface PendingProduct {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
  description?: string;
  category?: string;
  price?: number;
  images?: string[];
  status: string;
  submittedAt: string;
}

/** Admin: user list */
export interface AdminUser {
  userId: number;
  username: string;
  email: string;
  role: string;
  status: 'ACTIVE' | 'BANNED' | 'PENDING';
  createdAt: string;
}

/** Admin: flash sale session create request */
export interface CreateFlashSaleRequest {
  name: string;
  startTime: string;
  endTime: string;
  description?: string;
}

export const adminApi = {
  /** List products for moderation (default: PENDING) */
  getPendingProducts: (params?: { page?: number; size?: number; status?: string }) =>
    apiClient.get<ApiResponse<PageResponse<PendingProduct>>>('/admin/products/pending', { params }),

  /** Approve a product */
  approveProduct: (productId: string, adminNote?: string) =>
    apiClient.post<ApiResponse<{ productId: string; status: string }>>(
      `/admin/products/${productId}/approve`,
      { note: adminNote }
    ),

  /** Reject a product */
  rejectProduct: (productId: string, reason: string) =>
    apiClient.post<ApiResponse<{ productId: string; status: string }>>(
      `/admin/products/${productId}/reject`,
      { reason }
    ),

  /** List all users */
  getUsers: (params?: { role?: string; status?: string; search?: string; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AdminUser>>>('/admin/users', { params }),

  /** Lock user account */
  lockUser: (userId: number, reason: string) =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/lock`, { reason }),

  /** Unlock user account */
  unlockUser: (userId: number, reason: string) =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/unlock`, { reason }),

  /** Create flash sale session (admin) */
  createFlashSaleSession: (data: CreateFlashSaleRequest) =>
    apiClient.post<ApiResponse<{ sessionId: number; status: string }>>('/flash-sales', data),

  /** Update flash sale session */
  updateFlashSaleSession: (sessionId: number, data: Partial<CreateFlashSaleRequest>) =>
    apiClient.put<ApiResponse<{ sessionId: number }>>(`/flash-sales/${sessionId}`, data),

  /** Delete flash sale session */
  deleteFlashSaleSession: (sessionId: number) =>
    apiClient.delete<ApiResponse<void>>(`/flash-sales/${sessionId}`),

  /** Get all sellers' Stripe onboarding accounts & status */
  getSellerStripeAccounts: () =>
    apiClient.get<ApiResponse<AdminSellerStripeAccountsResponse>>('/stripe/onboarding/admin/sellers'),
};

export interface AdminSellerStripeSummary {
  totalSellers: number;
  completedSellers: number;
  pendingSellers: number;
  inProgressSellers: number;
  suspendedSellers: number;
}

export interface AdminSellerStripeAccountItem {
  sellerId: number;
  stripeAccountId: string;
  accountStatus: string;
  detailsSubmitted: boolean;
  chargesEnabled: boolean;
  payoutsEnabled: boolean;
  onboardingStatus: string;
  expressDashboardUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminSellerStripeAccountsResponse {
  summary: AdminSellerStripeSummary;
  accounts: AdminSellerStripeAccountItem[];
}
