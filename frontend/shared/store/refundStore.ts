import { create } from 'zustand';
import {
  refundApi,
  type RefundResponse,
  type FullRefundCreatedResponse,
  type FullRefundStatus,
} from '../api/refund.api';

interface RefundState {
  refunds: RefundResponse[];
  currentRefund: RefundResponse | null;
  fullRefundStatus: FullRefundStatus | null;
  isLoading: boolean;
  error: string | null;
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };

  requestFullRefund: (
    parentOrderId: number,
    reason: string,
    evidenceImages?: string[]
  ) => Promise<FullRefundCreatedResponse>;
  getFullRefundStatus: (parentOrderId: number) => Promise<void>;
  requestPartialRefund: (
    orderId: number,
    reason: string,
    items: { orderItemId: number; quantity: number; itemReason?: string }[],
    evidenceImages?: string[]
  ) => Promise<void>;
  requestMultiPartialRefund: (
    parentOrderId: number,
    reason: string,
    items: { orderItemId: number; quantity: number; itemReason?: string }[],
    evidenceImages?: string[]
  ) => Promise<void>;
  fetchRefundsByOrder: (orderId: number) => Promise<void>;
  fetchMyRefunds: (params?: { status?: string; page?: number; size?: number }) => Promise<void>;
  clearCurrentRefund: () => void;
}

export const useRefundStore = create<RefundState>((set) => ({
  refunds: [],
  currentRefund: null,
  fullRefundStatus: null,
  isLoading: false,
  error: null,
  pagination: {
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  },

  requestFullRefund: async (parentOrderId, reason, evidenceImages) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await refundApi.requestFullRefund(parentOrderId, {
        reason,
        evidenceImages,
      });
      set({ isLoading: false });
      return data.data!;
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to request refund',
        isLoading: false,
      });
      throw err;
    }
  },

  getFullRefundStatus: async (parentOrderId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await refundApi.getFullRefundStatus(parentOrderId);
      set({ fullRefundStatus: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to get refund status',
        isLoading: false,
      });
    }
  },

  requestPartialRefund: async (orderId, reason, items, evidenceImages) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await refundApi.requestPartialRefund(orderId, {
        reason,
        items,
        evidenceImages,
      });
      set({ currentRefund: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to request partial refund',
        isLoading: false,
      });
      throw err;
    }
  },

  requestMultiPartialRefund: async (parentOrderId, reason, items, evidenceImages) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await refundApi.requestMultiPartialRefund(parentOrderId, {
        reason,
        items,
        evidenceImages,
      });
      set({ isLoading: false });
      return data.data;
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to request partial refund',
        isLoading: false,
      });
      throw err;
    }
  },

  fetchRefundsByOrder: async (orderId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await refundApi.getRefundsByOrder(orderId);
      set({ refunds: data.data || [], isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch refunds',
        isLoading: false,
      });
    }
  },

  fetchMyRefunds: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await refundApi.getMyRefunds(params);
      const payload = data.data;
      set({
        refunds: payload?.content || [],
        pagination: {
          page: params?.page ?? 0,
          size: params?.size ?? 20,
          totalElements: payload?.totalElements ?? 0,
          totalPages: payload?.totalPages ?? 0,
        },
        isLoading: false,
      });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch refunds',
        isLoading: false,
      });
    }
  },

  clearCurrentRefund: () => set({ currentRefund: null }),
}));
