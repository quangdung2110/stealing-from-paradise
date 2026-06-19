import { create } from 'zustand';
import {
  sellerApi,
  type SellerDashboardStats,
  type StripeOnboardingStatus,
  type SellerEarnings,
  type StripeDashboardLink,
} from '../api/seller.api';

interface SellerState {
  dashboardStats: SellerDashboardStats | null;
  stripeStatus: StripeOnboardingStatus | null;
  earnings: SellerEarnings | null;
  stripeDashboardLink: StripeDashboardLink | null;
  isLoading: boolean;
  error: string | null;

  fetchDashboardStats: () => Promise<void>;
  fetchStripeStatus: () => Promise<void>;
  startStripeOnboarding: () => Promise<string>;
  refreshStripeLink: () => Promise<string>;
  fetchEarnings: () => Promise<void>;
  fetchStripeDashboardLink: () => Promise<void>;
  clearError: () => void;
}

export const useSellerStore = create<SellerState>((set) => ({
  dashboardStats: null,
  stripeStatus: null,
  earnings: null,
  stripeDashboardLink: null,
  isLoading: false,
  error: null,

  fetchDashboardStats: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await sellerApi.getDashboardStats();
      set({ dashboardStats: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch dashboard stats',
        isLoading: false,
      });
    }
  },

  fetchStripeStatus: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await sellerApi.getStripeStatus();
      set({ stripeStatus: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch Stripe status',
        isLoading: false,
      });
    }
  },

  startStripeOnboarding: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await sellerApi.startStripeOnboarding();
      set({ isLoading: false });
      return data.data!.onboardingUrl;
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to start Stripe onboarding',
        isLoading: false,
      });
      throw err;
    }
  },

  refreshStripeLink: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await sellerApi.refreshStripeLink();
      set({ isLoading: false });
      return data.data!.onboardingUrl;
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to refresh Stripe link',
        isLoading: false,
      });
      throw err;
    }
  },

  fetchEarnings: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await sellerApi.getEarnings();
      set({ earnings: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch earnings',
        isLoading: false,
      });
    }
  },

  fetchStripeDashboardLink: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await sellerApi.getStripeDashboardLink();
      set({ stripeDashboardLink: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch Stripe dashboard link',
        isLoading: false,
      });
    }
  },

  clearError: () => set({ error: null }),
}));
