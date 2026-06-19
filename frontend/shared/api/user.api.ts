import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

// ─── User Profile ─────────────────────────────────────────────────────────────

export interface UserProfileResponse {
  userId: number;
  username: string;
  email: string;
  phone?: string;
  fullName?: string;
  avatarUrl?: string;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface SellerPublicInfo {
  sellerId: number;
  sellerName: string;
  avatarUrl?: string;
  joinedAt: string;
  productCount: number;
}

export interface UserProfileUpdateRequest {
  fullName?: string;
  avatarUrl?: string;
  phone?: string;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  objectKey: string;
  cdnUrl: string;
  expiresIn: number;
}

// ─── Address ───────────────────────────────────────────────────────────────────

export interface AddressResponse {
  addressId: number;
  provinceId: number;
  districtId: number;
  fullAddress: string;
  isDefault: boolean;
}

export interface AddressCreateRequest {
  provinceId: number;
  districtId: number;
  fullAddress: string;
  isDefault?: boolean;
}

export interface AddressUpdateRequest {
  provinceId?: number;
  districtId?: number;
  fullAddress?: string;
  isDefault?: boolean;
}

// ─── Internal User ────────────────────────────────────────────────────────────

export interface InternalUserInfoResponse {
  userId: number;
  username: string;
  email: string;
  phone?: string;
  role: string;
  status: string;
}

// ─── Admin ─────────────────────────────────────────────────────────────────────

export interface AdminUserDetail extends AdminUser {
  phone?: string;
  fullName?: string;
  avatarUrl?: string;
  addresses?: AddressResponse[];
  banHistory?: BanHistoryResponse[];
}

export interface BanHistoryResponse {
  id: number;
  bannedBy: string;
  reason: string;
  bannedAt: string;
  unbannedAt?: string;
  unbannedBy?: string;
}

export interface AdminUser {
  userId: number;
  username: string;
  email: string;
  role: string;
  status: 'ACTIVE' | 'BANNED' | 'PENDING';
  createdAt: string;
}

// ─── API ─────────────────────────────────────────────────────────────────────

export const userApi = {
  // Public seller info (no auth required)
  getSellerPublic: (sellerId: number) =>
    apiClient.get<ApiResponse<SellerPublicInfo>>(`/users/sellers/${sellerId}`),

  // Profile
  getProfile: () =>
    apiClient.get<ApiResponse<UserProfileResponse>>('/users/me'),

  updateProfile: (data: UserProfileUpdateRequest) =>
    apiClient.put<ApiResponse<UserProfileResponse>>('/users/me', data),

  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    apiClient.post<ApiResponse<void>>('/users/me/change-password', data),

  getAvatarPresignedUrl: (contentType: string) =>
    apiClient.get<ApiResponse<PresignedUrlResponse>>('/users/me/avatar/presigned-url', {
      params: { contentType },
    }),

  registerAsSeller: () =>
    apiClient.post<ApiResponse<void>>('/users/me/roles/seller'),

  getNotificationPreferences: () =>
    apiClient.get<ApiResponse<{ preferences: Record<string, boolean> }>>('/users/me/notification-preferences'),

  updateNotificationPreferences: (preferences: Record<string, boolean>) =>
    apiClient.put<ApiResponse<{ preferences: Record<string, boolean> }>>('/users/me/notification-preferences', { preferences }),

  updateAvatar: (avatarUrl: string) =>
    apiClient.put<ApiResponse<void>>('/users/me/avatar', { avatarUrl }),

};

export const adminUserApi = {
  getUserDetail: (userId: number) =>
    apiClient.get<ApiResponse<AdminUserDetail>>(`/admin/users/${userId}`),

  getUsers: (params?: { role?: string; status?: string; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AdminUser>>>('/admin/users', { params }),

  lockUser: (userId: number, body: { reason: string }) =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/lock`, body),

  unlockUser: (userId: number, body: { reason: string }) =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/unlock`, body),
};
