import { create } from 'zustand';
import {
  productApi,
  type ProductDetail,
  type ProductListItem,
} from '../api/product.api';

interface ProductState {
  products: ProductListItem[];
  currentProduct: ProductDetail | null;
  isLoading: boolean;
  error: string | null;
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
  filters: {
    category?: string;
    search?: string;
    sort?: string;
  };

  fetchProducts: (params?: {
    category?: string;
    search?: string;
    page?: number;
    size?: number;
    sort?: string;
  }) => Promise<void>;
  fetchProductById: (productId: string) => Promise<void>;
  clearCurrentProduct: () => void;
  setFilters: (filters: Partial<ProductState['filters']>) => void;
}

export const useProductStore = create<ProductState>((set, get) => ({
  products: [],
  currentProduct: null,
  isLoading: false,
  error: null,
  pagination: {
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  },
  filters: {},

  fetchProducts: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await productApi.getProducts(params);
      const payload = data.data;
      if (payload) {
        set({
          products: payload.content ?? (Array.isArray(payload) ? payload : []),
          pagination: {
            page: 0,
            size: params?.size ?? 20,
            totalElements: Array.isArray(payload) ? payload.length : 0,
            totalPages: 1,
          },
          isLoading: false,
        });
      }
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch products',
        isLoading: false,
      });
    }
  },

  fetchProductById: async (productId) => {
    set({ isLoading: true, error: null, currentProduct: null });
    try {
      const { data } = await productApi.getProductById(productId);
      set({ currentProduct: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch product',
        isLoading: false,
      });
    }
  },

  clearCurrentProduct: () => set({ currentProduct: null }),

  setFilters: (filters) =>
    set((state) => ({ filters: { ...state.filters, ...filters } })),
}));
