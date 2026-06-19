import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface FlashSaleItem {
  id: number;
  sessionId: number;
  sellerId?: number;
  skuCode: string;
  productName?: string;
  flashPrice: number;
  originalPrice?: number;
  flashStock: number;
  soldQty: number;
  limitPerUser: number;
  status: string;
  imageUrl?: string;
}

export interface FlashSaleSession {
  id: number;
  name: string;
  startTime: string;
  endTime: string;
  status: 'UPCOMING' | 'ACTIVE' | 'ENDED' | 'CANCELLED';
  secondsRemaining?: number;
  isEnded?: boolean;
  createdAt?: string;
  items?: FlashSaleItem[];
}

interface BackendSession {
  sessionId: number;
  name: string;
  status: string;
  startTime: string;
  endTime: string;
  secondsRemaining: number;
  isEnded: boolean;
  createdAt: string;
  updatedAt: string;
}

interface BackendItem {
  id: number;
  sessionId: number;
  sellerId?: number;
  skuCode: string;
  flashPrice: number;
  flashStock: number;
  limitPerUser: number;
  soldQty: number;
  status: string;
  createdAt: string;
  updatedAt: string;
  productName?: string;
  originalPrice?: number;
  imageUrl?: string;
}

function mapSession(s: BackendSession): FlashSaleSession {
  // Backend emits "LIVE" for an in-progress session; normalize to "ACTIVE".
  const status: string = s.status === 'LIVE' ? 'ACTIVE' : s.status;
  return {
    id: s.sessionId,
    name: s.name,
    startTime: s.startTime,
    endTime: s.endTime,
    status: status as FlashSaleSession['status'],
    secondsRemaining: s.secondsRemaining,
    isEnded: s.isEnded,
    createdAt: s.createdAt,
  };
}

function mapItem(i: BackendItem): FlashSaleItem {
  return {
    id: i.id,
    sessionId: i.sessionId,
    sellerId: i.sellerId,
    skuCode: i.skuCode,
    flashPrice: i.flashPrice,
    flashStock: i.flashStock,
    soldQty: i.soldQty,
    limitPerUser: i.limitPerUser,
    status: i.status,
    productName: i.productName,
    originalPrice: i.originalPrice,
    imageUrl: i.imageUrl,
  };
}

export const flashSaleApi = {
  /** Get all flash sale sessions (public). Backend returns SessionResponse[] as data. */
  getSessions: async (params?: { status?: string }) => {
    const res = await apiClient.get<ApiResponse<BackendSession[]>>('/flash-sales', { params });
    const sessions: BackendSession[] = res.data.data ?? [];
    return {
      ...res,
      data: {
        ...res.data,
        data: {
          content: sessions.map(mapSession),
          totalElements: sessions.length,
          totalPages: 1,
        },
      },
    };
  },

  /** Get one session with its items */
  getSession: async (sessionId: number) => {
    const res = await apiClient.get<ApiResponse<{ session: BackendSession; items: BackendItem[] }>>(`/flash-sales/${sessionId}`);
    const data = res.data.data;
    return {
      ...res,
      data: {
        ...res.data,
        data: data ? {
          ...mapSession(data.session),
          items: (data.items ?? []).map(mapItem),
        } : null,
      },
    };
  },

  /** Create a flash sale session (admin) */
  createSession: (data: { name: string; startTime: string; endTime: string; description?: string; registrationWindowMinutes?: number }) =>
    apiClient.post<ApiResponse<BackendSession>>('/flash-sales', data),

  /** Seller registers a SKU into an upcoming flash sale session. */
  registerItem: (sessionId: number, data: { skuCode: string; flashPrice: number; flashStock: number; limitPerUser?: number }) =>
    apiClient.post<ApiResponse<BackendItem>>(`/flash-sales/${sessionId}/items`, data),

  /** Admin approves a seller's registered item. */
  approveItem: (sessionId: number, itemId: number, note?: string) =>
    apiClient.post<ApiResponse<BackendItem>>(`/flash-sales/${sessionId}/items/${itemId}/approve`, { note }),

  /** Admin rejects a seller's registered item. */
  rejectItem: (sessionId: number, itemId: number, rejectReason: string) =>
    apiClient.post<ApiResponse<BackendItem>>(`/flash-sales/${sessionId}/items/${itemId}/reject`, { rejectReason }),
};
