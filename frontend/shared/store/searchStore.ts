import { create } from 'zustand';
import {
  productApi,
  type ProductDetail,
} from '../api/product.api';

interface SearchState {
  query: string;
  results: ProductDetail[];
  isLoading: boolean;
  error: string | null;
  pagination: {
    page: number;
    size: number;
  };

  search: (q: string, params?: { category?: string; page?: number; size?: number }) => Promise<void>;
  setQuery: (query: string) => void;
  clearResults: () => void;
}

export const useSearchStore = create<SearchState>((set) => ({
  query: '',
  results: [],
  isLoading: false,
  error: null,
  pagination: {
    page: 0,
    size: 20,
  },

  search: async (q, params) => {
    set({ isLoading: true, error: null, query: q });
    try {
      const { data } = await productApi.searchProducts(q, params);
      set({ results: data.data || [], isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Search failed',
        isLoading: false,
      });
    }
  },

  setQuery: (query) => set({ query }),
  clearResults: () => set({ results: [], query: '' }),
}));
