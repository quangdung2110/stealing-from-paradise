import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

/** Mirrors backend WishlistItemResponse (product-service). */
export interface WishlistItem {
  productId: string;
  productName: string;
  productSlug?: string | null;
  thumbnailUrl?: string | null;
  minPrice?: number | null;
  productStatus?: string;
  available?: boolean;
  addedAt?: string;
}

export const wishlistApi = {
  /** GET /wishlist — paginated favourites of the current buyer. */
  getWishlist: (page = 0, size = 60) =>
    apiClient.get<ApiResponse<PageResponse<WishlistItem>>>('/wishlist', { params: { page, size } }),

  /** POST /wishlist/items — idempotent add (backend returns ALREADY_EXISTS gracefully). */
  addItem: (productId: string) =>
    apiClient.post<ApiResponse<WishlistItem>>('/wishlist/items', { productId }),

  /** DELETE /wishlist/items/{productId} */
  removeItem: (productId: string) =>
    apiClient.delete<ApiResponse<void>>(`/wishlist/items/${productId}`),

  /** GET /wishlist/items/{productId} — true if the product is already wished. */
  isInWishlist: (productId: string) =>
    apiClient.get<ApiResponse<boolean>>(`/wishlist/items/${productId}`),
};
