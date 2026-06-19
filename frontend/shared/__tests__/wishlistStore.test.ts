import { vi, describe, it, expect, beforeEach } from 'vitest';

// Mock the wishlist API so the store tests stay pure (no network).
vi.mock('../api/wishlist.api', () => ({
  wishlistApi: {
    getWishlist: vi.fn(),
    addItem: vi.fn(() => Promise.resolve({ data: { success: true } })),
    removeItem: vi.fn(() => Promise.resolve({ data: { success: true } })),
    isInWishlist: vi.fn(),
  },
}));

import { useWishlistStore } from '../store/wishlistStore';
import { wishlistApi } from '../api/wishlist.api';

const item = (id: string) => ({ productId: id, productName: `SP ${id}`, minPrice: 1000 });

beforeEach(() => {
  vi.clearAllMocks();
  useWishlistStore.setState({ items: [], ids: new Set(), isLoading: false, error: null });
});

describe('wishlistStore.fetchWishlist', () => {
  it('loads items and builds the productId set', async () => {
    (wishlistApi.getWishlist as any).mockResolvedValue({
      data: { success: true, data: { content: [item('a'), item('b')], totalElements: 2, totalPages: 1, last: true } },
    });
    await useWishlistStore.getState().fetchWishlist();
    const s = useWishlistStore.getState();
    expect(s.items).toHaveLength(2);
    expect(s.ids.has('a')).toBe(true);
    expect(s.ids.has('b')).toBe(true);
    expect(s.isLoading).toBe(false);
  });

  it('records the API error message and stops loading on failure', async () => {
    (wishlistApi.getWishlist as any).mockRejectedValue({ response: { data: { message: 'boom' } } });
    await useWishlistStore.getState().fetchWishlist();
    const s = useWishlistStore.getState();
    expect(s.items).toHaveLength(0);
    expect(s.error).toBe('boom');
    expect(s.isLoading).toBe(false);
  });
});

describe('wishlistStore.toggle (optimistic heart)', () => {
  it('flips the heart on immediately, then calls addItem when not wished', async () => {
    const pending = useWishlistStore.getState().toggle('x');
    // optimistic: đổi màu trước cả khi API trả lời
    expect(useWishlistStore.getState().ids.has('x')).toBe(true);
    await pending;
    expect(wishlistApi.addItem).toHaveBeenCalledWith('x');
    expect(useWishlistStore.getState().ids.has('x')).toBe(true);
  });

  it('calls removeItem and clears the id when already wished', async () => {
    useWishlistStore.setState({ ids: new Set(['x']) });
    await useWishlistStore.getState().toggle('x');
    expect(wishlistApi.removeItem).toHaveBeenCalledWith('x');
    expect(useWishlistStore.getState().ids.has('x')).toBe(false);
  });

  it('reverts the optimistic flip when the API call fails', async () => {
    (wishlistApi.addItem as any).mockRejectedValue({ response: { data: { message: 'fail' } } });
    await useWishlistStore.getState().toggle('x');
    const s = useWishlistStore.getState();
    expect(s.ids.has('x')).toBe(false);
    expect(s.error).toBe('fail');
  });
});

describe('wishlistStore.remove', () => {
  it('removes optimistically and restores items + ids when the API fails', async () => {
    useWishlistStore.setState({ items: [item('a')] as any, ids: new Set(['a']) });
    (wishlistApi.removeItem as any).mockRejectedValue({ response: { data: { message: 'no' } } });
    await useWishlistStore.getState().remove('a');
    const s = useWishlistStore.getState();
    expect(s.items).toHaveLength(1);
    expect(s.ids.has('a')).toBe(true);
    expect(s.error).toBe('no');
  });

  it('keeps the item removed when the API succeeds', async () => {
    // mockRejectedValue ở test trước vẫn dính (clearAllMocks không xóa implementation) — ép lại resolve.
    (wishlistApi.removeItem as any).mockResolvedValue({ data: { success: true } });
    useWishlistStore.setState({ items: [item('a'), item('b')] as any, ids: new Set(['a', 'b']) });
    await useWishlistStore.getState().remove('a');
    const s = useWishlistStore.getState();
    expect(s.items.map((i) => i.productId)).toEqual(['b']);
    expect(s.ids.has('a')).toBe(false);
  });
});
