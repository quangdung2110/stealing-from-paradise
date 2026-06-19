import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export type BannerPosition = 'HERO' | 'SIDEBAR' | 'POPUP';

export interface Banner {
  id: string;
  title: string;
  imageUrl: string;
  position: BannerPosition;
  active: boolean;
  startsAt?: string | null;
  endsAt?: string | null;
}

export interface BannerRequest {
  title: string;
  imageUrl: string;
  position: BannerPosition;
  active?: boolean;
  startsAt?: string | null;
  endsAt?: string | null;
}

export const bannerApi = {
  list: () => apiClient.get<ApiResponse<Banner[]>>('/admin/banners'),
  create: (body: BannerRequest) => apiClient.post<ApiResponse<Banner>>('/admin/banners', body),
  update: (id: string, body: BannerRequest) => apiClient.put<ApiResponse<Banner>>(`/admin/banners/${id}`, body),
  remove: (id: string) => apiClient.delete<ApiResponse<void>>(`/admin/banners/${id}`),
  getUploadUrl: (filename: string) =>
    apiClient.get<ApiResponse<{ uploadUrl: string }>>('/admin/banners/presigned-url', { params: { filename } }),
};

/** Upload a banner image via presigned URL; returns the stored object URL. */
export async function uploadBannerImage(file: File): Promise<string> {
  const { data: resp } = await bannerApi.getUploadUrl(file.name);
  const uploadUrl = resp.data!.uploadUrl;
  await fetch(uploadUrl, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } });
  return uploadUrl.split('?')[0];
}
