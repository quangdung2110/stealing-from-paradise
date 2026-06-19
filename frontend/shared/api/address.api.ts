import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface UserAddress {
  addressId: number;
  provinceId: number;
  districtId: number;
  fullAddress: string;
  isDefault: boolean;
}

/** Backend identity-service AddressResponse uses snake_case fields */
interface RawAddressResponse {
  address_id: number;
  province_id: number;
  district_id: number;
  full_address: string;
  is_default: boolean;
}

interface AddressInput {
  provinceId?: number;
  districtId?: number;
  fullAddress?: string;
  isDefault?: boolean;
}

function mapAddress(raw: RawAddressResponse): UserAddress {
  return {
    addressId: raw.address_id,
    provinceId: raw.province_id,
    districtId: raw.district_id,
    fullAddress: raw.full_address,
    isDefault: raw.is_default,
  };
}

/** Backend AddressCreate/UpdateRequest expect snake_case fields */
function toRawAddress(data: AddressInput) {
  return {
    province_id: data.provinceId,
    district_id: data.districtId,
    full_address: data.fullAddress,
    is_default: data.isDefault,
  };
}

export const addressApi = {
  list: () =>
    apiClient.get<ApiResponse<RawAddressResponse[]>>('/users/me/addresses').then(res => ({
      ...res,
      data: {
        ...res.data,
        data: (res.data.data ?? []).map(mapAddress),
      } as ApiResponse<UserAddress[]>,
    })),

  create: (data: {
    provinceId: number;
    districtId: number;
    fullAddress: string;
    isDefault?: boolean;
  }) =>
    apiClient.post<ApiResponse<RawAddressResponse>>('/users/me/addresses', toRawAddress(data)).then(res => ({
      ...res,
      data: {
        ...res.data,
        data: res.data.data ? mapAddress(res.data.data) : undefined,
      } as ApiResponse<UserAddress>,
    })),

  update: (addressId: number, data: Partial<{
    provinceId: number;
    districtId: number;
    fullAddress: string;
    isDefault: boolean;
  }>) =>
    apiClient.put<ApiResponse<RawAddressResponse>>(`/users/me/addresses/${addressId}`, toRawAddress(data)).then(res => ({
      ...res,
      data: {
        ...res.data,
        data: res.data.data ? mapAddress(res.data.data) : undefined,
      } as ApiResponse<UserAddress>,
    })),

  remove: (addressId: number) =>
    apiClient.delete<ApiResponse<void>>(`/users/me/addresses/${addressId}`),
};
