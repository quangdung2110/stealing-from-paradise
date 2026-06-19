import { create } from 'zustand';
import {
  orderApi,
  type CheckoutRequest,
  type CheckoutResponse,
  type Order,
  type OrderSummary,
  type ParentOrderDetail,
  type CancelOrderRequest,
  type UpdateTrackingRequest,
  type SellerOrderSummary,
  type OrderStatus,
} from '../api/order.api';
import type { PageResponse } from '../types/api';

interface OrderState {
  orders: OrderSummary[];
  currentOrder: Order | null;
  currentParentOrder: ParentOrderDetail | null;
  checkoutResult: CheckoutResponse | null;
  sellerOrders: SellerOrderSummary[];
  isLoading: boolean;
  error: string | null;
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };

  checkout: (req: CheckoutRequest) => Promise<CheckoutResponse>;
  fetchOrders: (params?: {
    status?: OrderStatus;
    from_date?: string;
    to_date?: string;
    page?: number;
    size?: number;
  }) => Promise<void>;
  fetchOrderById: (orderId: number) => Promise<void>;
  fetchParentOrder: (parentOrderId: number) => Promise<void>;
  cancelOrder: (orderId: number, req: CancelOrderRequest) => Promise<void>;
  updateTracking: (orderId: number, req: UpdateTrackingRequest) => Promise<void>;
  confirmReceived: (orderId: number) => Promise<void>;
  returnToSender: (orderId: number, formData: FormData) => Promise<void>;
  fetchSellerOrders: (params?: {
    status?: OrderStatus;
    from_date?: string;
    to_date?: string;
    page?: number;
    size?: number;
  }) => Promise<void>;
  clearCheckoutResult: () => void;
  clearCurrentOrder: () => void;
  clearCurrentParentOrder: () => void;
}

export const useOrderStore = create<OrderState>((set, get) => ({
  orders: [],
  currentOrder: null,
  currentParentOrder: null,
  checkoutResult: null,
  sellerOrders: [],
  isLoading: false,
  error: null,
  pagination: {
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  },

  checkout: async (req) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await orderApi.checkout(req);
      const result = data.data!;
      set({ checkoutResult: result, isLoading: false });
      return result;
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Checkout failed',
        isLoading: false,
      });
      throw err;
    }
  },

  fetchOrders: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await orderApi.getOrders(params);
      const page: PageResponse<OrderSummary> | undefined = data.data;
      set({
        orders: page?.content || [],
        pagination: {
          page: page?.page ?? params?.page ?? 0,
          size: page?.size ?? params?.size ?? 20,
          totalElements: page?.totalElements ?? 0,
          totalPages: page?.totalPages ?? 0,
        },
        isLoading: false,
      });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch orders',
        isLoading: false,
      });
    }
  },

  fetchOrderById: async (orderId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await orderApi.getOrderById(orderId);
      set({ currentOrder: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch order',
        isLoading: false,
      });
    }
  },

  fetchParentOrder: async (parentOrderId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await orderApi.getParentOrder(parentOrderId);
      set({ currentParentOrder: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch parent order',
        isLoading: false,
      });
    }
  },

  cancelOrder: async (orderId, req) => {
    set({ isLoading: true, error: null });
    try {
      await orderApi.cancelOrder(orderId, req);
      await get().fetchOrderById(orderId);
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to cancel order',
        isLoading: false,
      });
      throw err;
    }
  },

  updateTracking: async (orderId, req) => {
    set({ isLoading: true, error: null });
    try {
      await orderApi.updateTracking(orderId, req);
      await get().fetchOrderById(orderId);
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to update tracking',
        isLoading: false,
      });
      throw err;
    }
  },

  confirmReceived: async (orderId) => {
    set({ isLoading: true, error: null });
    try {
      await orderApi.confirmReceived(orderId);
      await get().fetchOrderById(orderId);
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to confirm receipt',
        isLoading: false,
      });
      throw err;
    }
  },

  returnToSender: async (orderId, formData) => {
    set({ isLoading: true, error: null });
    try {
      await orderApi.returnToSender(orderId, formData);
      await get().fetchOrderById(orderId);
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to return to sender',
        isLoading: false,
      });
      throw err;
    }
  },

  fetchSellerOrders: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await orderApi.getSellerOrders(params);
      const page: PageResponse<SellerOrderSummary> | undefined = data.data;
      set({
        sellerOrders: page?.content || [],
        pagination: {
          page: page?.page ?? params?.page ?? 0,
          size: page?.size ?? params?.size ?? 20,
          totalElements: page?.totalElements ?? 0,
          totalPages: page?.totalPages ?? 0,
        },
        isLoading: false,
      });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch seller orders',
        isLoading: false,
      });
    }
  },

  clearCheckoutResult: () => set({ checkoutResult: null }),
  clearCurrentOrder: () => set({ currentOrder: null }),
  clearCurrentParentOrder: () => set({ currentParentOrder: null }),
}));
