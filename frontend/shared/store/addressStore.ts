import { create } from 'zustand';
import {
  addressApi,
  type UserAddress,
} from '../api/address.api';

interface AddressState {
  addresses: UserAddress[];
  defaultAddress: UserAddress | null;
  isLoading: boolean;
  error: string | null;

  fetchAddresses: () => Promise<void>;
  createAddress: (data: {
    provinceId: number;
    districtId: number;
    fullAddress: string;
    isDefault?: boolean;
  }) => Promise<void>;
  updateAddress: (
    addressId: number,
    data: Partial<{
      provinceId: number;
      districtId: number;
      fullAddress: string;
      isDefault: boolean;
    }>
  ) => Promise<void>;
  removeAddress: (addressId: number) => Promise<void>;
  clearError: () => void;
}

export const useAddressStore = create<AddressState>((set, get) => ({
  addresses: [],
  defaultAddress: null,
  isLoading: false,
  error: null,

  fetchAddresses: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await addressApi.list();
      const addresses = data.data || [];
      const defaultAddr = addresses.find((a) => a.isDefault) || null;
      set({ addresses, defaultAddress: defaultAddr, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch addresses',
        isLoading: false,
      });
    }
  },

  createAddress: async (addressData) => {
    set({ isLoading: true, error: null });
    try {
      await addressApi.create(addressData);
      await get().fetchAddresses();
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to create address',
        isLoading: false,
      });
      throw err;
    }
  },

  updateAddress: async (addressId, addressData) => {
    set({ isLoading: true, error: null });
    try {
      await addressApi.update(addressId, addressData);
      await get().fetchAddresses();
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to update address',
        isLoading: false,
      });
      throw err;
    }
  },

  removeAddress: async (addressId) => {
    set({ isLoading: true, error: null });
    try {
      await addressApi.remove(addressId);
      await get().fetchAddresses();
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to remove address',
        isLoading: false,
      });
      throw err;
    }
  },

  clearError: () => set({ error: null }),
}));
