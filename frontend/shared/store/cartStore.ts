import { create } from 'zustand';
import { cartApi, type Cart, type CartItem } from '../api/cart.api';

interface CartState {
  cart: Cart | null;
  isLoading: boolean;
  error: string | null;

  fetchCart: () => Promise<void>;
  addToCart: (variantIdOrSku: string, quantity: number, fsItemId?: number) => Promise<void>;
  updateQuantity: (itemId: string, quantity: number) => Promise<void>;
  removeFromCart: (itemId: string) => Promise<void>;
  clearCart: () => Promise<void>;

  getTotalItems: () => number;
  getTotalAmount: () => number;
  getItemCount: () => number;
}

const emptyCart = (): Cart => ({ sellers: [], totalItems: 0, subtotal: 0 });

const getLinePrice = (item: CartItem) => item.flashPrice ?? item.unitPrice ?? 0;

const matchesCartItem = (item: CartItem, itemId: string) =>
  item.cartItemId === itemId || item.variantId === itemId || item.skuCode === itemId;

const recalculateCart = (cart: Cart): Cart => {
  const sellers = cart.sellers
    .map((seller) => {
      const items = seller.items.map((item) => ({
        ...item,
        subtotal: getLinePrice(item) * item.quantity,
      }));
      return {
        ...seller,
        items,
        sellerSubtotal: items.reduce((sum, item) => sum + (item.subtotal ?? 0), 0),
      };
    })
    .filter((seller) => seller.items.length > 0);

  return {
    ...cart,
    sellers,
    totalItems: sellers.reduce(
      (sum, seller) => sum + seller.items.reduce((itemSum, item) => itemSum + item.quantity, 0),
      0,
    ),
    subtotal: sellers.reduce((sum, seller) => sum + (seller.sellerSubtotal ?? 0), 0),
  };
};

const updateCartQuantity = (cart: Cart, itemId: string, quantity: number): Cart =>
  recalculateCart({
    ...cart,
    sellers: cart.sellers.map((seller) => ({
      ...seller,
      items: seller.items.map((item) =>
        matchesCartItem(item, itemId) ? { ...item, quantity } : item,
      ),
    })),
  });

const removeCartItem = (cart: Cart, itemId: string): Cart =>
  recalculateCart({
    ...cart,
    sellers: cart.sellers.map((seller) => ({
      ...seller,
      items: seller.items.filter((item) => !matchesCartItem(item, itemId)),
    })),
  });

const addCartItem = (cart: Cart, variantId: string, quantity: number, fsItemId?: number): Cart => {
  let found = false;
  const sellers = cart.sellers.map((seller) => ({
    ...seller,
    items: seller.items.map((item) => {
      if (!matchesCartItem(item, variantId)) return item;
      found = true;
      return { ...item, quantity: item.quantity + quantity };
    }),
  }));

  if (!found) {
    const optimisticItem: CartItem = {
      cartItemId: `optimistic:${variantId}`,
      variantId,
      skuCode: variantId,
      productName: 'Đang đồng bộ giỏ hàng',
      variantName: 'Sản phẩm mới',
      unitPrice: 0,
      quantity,
      stockAvailable: quantity,
      isFlash: !!fsItemId,
      fsItemId: fsItemId ?? null,
      subtotal: 0,
      addedAt: new Date().toISOString(),
    };
    const firstSeller = sellers[0];
    if (firstSeller) {
      sellers[0] = { ...firstSeller, items: [...firstSeller.items, optimisticItem] };
    } else {
      sellers.push({
        sellerId: 0,
        sellerName: 'Đang cập nhật',
        items: [optimisticItem],
      });
    }
  }

  return recalculateCart({ ...cart, sellers });
};

export const useCartStore = create<CartState>((set, get) => ({
  cart: null,
  isLoading: false,
  error: null,

  fetchCart: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await cartApi.getCart();
      set({ cart: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch cart',
        isLoading: false,
      });
    }
  },

  addToCart: async (variantIdOrSku, quantity, fsItemId) => {
    const previousCart = get().cart;
    set({
      cart: previousCart ? addCartItem(previousCart, variantIdOrSku, quantity, fsItemId) : previousCart,
      isLoading: true,
      error: null,
    });
    try {
      await cartApi.addItem(variantIdOrSku, quantity, fsItemId);
      await get().fetchCart();
    } catch (err: any) {
      set({
        cart: previousCart,
        error: err?.response?.data?.message || 'Failed to add item to cart',
        isLoading: false,
      });
      throw err;
    }
  },

  updateQuantity: async (itemId, quantity) => {
    const previousCart = get().cart;
    set({
      cart: previousCart ? updateCartQuantity(previousCart, itemId, quantity) : previousCart,
      isLoading: true,
      error: null,
    });
    try {
      await cartApi.updateItemQuantity(itemId, quantity);
      await get().fetchCart();
    } catch (err: any) {
      set({
        cart: previousCart,
        error: err?.response?.data?.message || 'Failed to update quantity',
        isLoading: false,
      });
      throw err;
    }
  },

  removeFromCart: async (itemId) => {
    const previousCart = get().cart;
    set({
      cart: previousCart ? removeCartItem(previousCart, itemId) : previousCart,
      isLoading: true,
      error: null,
    });
    try {
      await cartApi.removeItem(itemId);
      await get().fetchCart();
    } catch (err: any) {
      set({
        cart: previousCart,
        error: err?.response?.data?.message || 'Failed to remove item',
        isLoading: false,
      });
      throw err;
    }
  },

  clearCart: async () => {
    const previousCart = get().cart;
    set({ cart: emptyCart(), isLoading: true, error: null });
    try {
      await cartApi.clearCart();
      set({ cart: emptyCart(), isLoading: false });
    } catch (err: any) {
      set({
        cart: previousCart,
        error: err?.response?.data?.message || 'Failed to clear cart',
        isLoading: false,
      });
    }
  },

  getTotalItems: () => {
    const state = get();
    return state.cart?.totalItems || 0;
  },

  getTotalAmount: () => {
    const state = get();
    return state.cart?.subtotal || 0;
  },

  getItemCount: () => {
    const state = get();
    if (!state.cart?.sellers) return 0;
    return state.cart.sellers.reduce((sum, seller) => sum + seller.items.length, 0);
  },
}));
