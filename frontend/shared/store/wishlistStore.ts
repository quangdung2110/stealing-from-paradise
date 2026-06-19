import { create } from 'zustand';
import { wishlistApi, type WishlistItem } from '../api/wishlist.api';

interface WishlistState {
  items: WishlistItem[];
  /** productIds đã thích — tra O(1) để tô màu nút tim trên card. */
  ids: Set<string>;
  isLoading: boolean;
  error: string | null;

  fetchWishlist: () => Promise<void>;
  /** Bỏ thích — optimistic, hoàn tác nếu API lỗi. */
  remove: (productId: string) => Promise<void>;
  /** Đảo trạng thái tim — optimistic, hoàn tác nếu API lỗi. */
  toggle: (productId: string) => Promise<void>;
  isWished: (productId: string) => boolean;
  reset: () => void;
}

export const useWishlistStore = create<WishlistState>((set, get) => ({
  items: [],
  ids: new Set<string>(),
  isLoading: false,
  error: null,

  fetchWishlist: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await wishlistApi.getWishlist();
      const items = data.data?.content ?? [];
      set({
        items,
        ids: new Set(items.map((i) => i.productId)),
        isLoading: false,
      });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Không tải được danh sách yêu thích',
        isLoading: false,
      });
    }
  },

  remove: async (productId) => {
    const prevItems = get().items;
    const prevIds = get().ids;
    set({
      items: prevItems.filter((i) => i.productId !== productId),
      ids: new Set(Array.from(prevIds).filter((id) => id !== productId)),
      error: null,
    });
    try {
      await wishlistApi.removeItem(productId);
    } catch (err: any) {
      set({
        items: prevItems,
        ids: prevIds,
        error: err?.response?.data?.message || 'Không bỏ thích được sản phẩm',
      });
    }
  },

  toggle: async (productId) => {
    const wished = get().ids.has(productId);
    const next = new Set(get().ids);
    if (wished) next.delete(productId);
    else next.add(productId);
    set({ ids: next, error: null });
    try {
      if (wished) await wishlistApi.removeItem(productId);
      else await wishlistApi.addItem(productId);
    } catch (err: any) {
      const revert = new Set(get().ids);
      if (wished) revert.add(productId);
      else revert.delete(productId);
      set({
        ids: revert,
        error: err?.response?.data?.message || 'Không cập nhật được yêu thích',
      });
    }
  },

  isWished: (productId) => get().ids.has(productId),

  reset: () => set({ items: [], ids: new Set<string>(), isLoading: false, error: null }),
}));
