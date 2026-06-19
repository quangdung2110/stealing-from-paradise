import { create } from 'zustand';
import {
  paymentApi,
  type PaymentDetail,
} from '../api/payment.api';

interface PaymentState {
  payment: PaymentDetail | null;
  isLoading: boolean;
  error: string | null;

  fetchPayment: (parentOrderId: number) => Promise<void>;
  clearPayment: () => void;
}

export const usePaymentStore = create<PaymentState>((set) => ({
  payment: null,
  isLoading: false,
  error: null,

  fetchPayment: async (parentOrderId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await paymentApi.getPayment(parentOrderId);
      set({ payment: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch payment',
        isLoading: false,
      });
    }
  },

  clearPayment: () => set({ payment: null }),
}));
