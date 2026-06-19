import { describe, it, expect, beforeEach, vi } from 'vitest';
import { cartApi, type Cart } from '../api/cart.api';
import { useCartStore } from '../store/cartStore';

vi.mock('../api/cart.api', () => ({
  cartApi: {
    getCart: vi.fn(),
    addItem: vi.fn(),
    updateItemQuantity: vi.fn(),
    removeItem: vi.fn(),
    clearCart: vi.fn(),
  },
}));

const cartWithQuantity = (quantity: number): Cart => ({
  sellers: [
    {
      sellerId: 1,
      sellerName: 'Shop A',
      items: [
        {
          cartItemId: 'buyer-1:variant-1',
          variantId: 'variant-1',
          skuCode: 'SKU1',
          productName: 'Item 1',
          variantName: 'Blue',
          unitPrice: 100,
          quantity,
          stockAvailable: 10,
          isFlash: false,
          subtotal: 100 * quantity,
        },
      ],
      sellerSubtotal: 100 * quantity,
    },
  ],
  totalItems: quantity,
  subtotal: 100 * quantity,
});

describe('cartStore - getters', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCartStore.setState({ cart: null, isLoading: false, error: null });
  });

  it('getTotalItems returns 0 for empty cart', () => {
    expect(useCartStore.getState().getTotalItems()).toBe(0);
  });

  it('getTotalItems returns totalItems from cart', () => {
    useCartStore.setState({
      cart: { sellers: [], totalItems: 5, subtotal: 100 },
    });
    expect(useCartStore.getState().getTotalItems()).toBe(5);
  });

  it('getTotalAmount returns 0 for empty cart', () => {
    expect(useCartStore.getState().getTotalAmount()).toBe(0);
  });

  it('getTotalAmount returns subtotal from cart', () => {
    useCartStore.setState({
      cart: { sellers: [], totalItems: 2, subtotal: 49.99 },
    });
    expect(useCartStore.getState().getTotalAmount()).toBe(49.99);
  });

  it('getItemCount returns 0 for empty cart', () => {
    expect(useCartStore.getState().getItemCount()).toBe(0);
  });

  it('getItemCount sums items across all sellers', () => {
    useCartStore.setState({
      cart: {
        sellers: [
          {
            sellerId: 1,
            sellerName: 'Shop A',
            items: [
              { id: 1, skuCode: 'SKU1', productName: 'Item 1', quantity: 2 },
              { id: 2, skuCode: 'SKU2', productName: 'Item 2', quantity: 1 },
            ] as any,
          },
          {
            sellerId: 2,
            sellerName: 'Shop B',
            items: [
              { id: 3, skuCode: 'SKU3', productName: 'Item 3', quantity: 3 },
            ] as any,
          },
        ],
        totalItems: 6,
        subtotal: 150,
      },
    });
    expect(useCartStore.getState().getItemCount()).toBe(3);
  });

  it('optimistically increments an existing item while addToCart waits for the server cart', async () => {
    const serverCart = cartWithQuantity(3);
    let resolveAdd!: () => void;

    vi.mocked(cartApi.addItem).mockReturnValue(
      new Promise((resolve) => {
        resolveAdd = () => resolve({} as any);
      }) as any,
    );
    vi.mocked(cartApi.getCart).mockResolvedValue({ data: { data: serverCart } } as any);
    useCartStore.setState({ cart: cartWithQuantity(1), isLoading: false, error: null });

    const pending = useCartStore.getState().addToCart('variant-1', 2);

    expect(useCartStore.getState().cart?.totalItems).toBe(3);
    expect(useCartStore.getState().cart?.subtotal).toBe(300);
    expect(useCartStore.getState().isLoading).toBe(true);

    resolveAdd();
    await pending;

    expect(useCartStore.getState().cart).toEqual(serverCart);
    expect(useCartStore.getState().isLoading).toBe(false);
  });

  it('optimistically updates quantity and rolls back when the server rejects it', async () => {
    const originalCart = cartWithQuantity(2);
    vi.mocked(cartApi.updateItemQuantity).mockRejectedValue(new Error('network down'));
    useCartStore.setState({ cart: originalCart, isLoading: false, error: null });

    await expect(
      useCartStore.getState().updateQuantity('buyer-1:variant-1', 5),
    ).rejects.toThrow('network down');

    expect(useCartStore.getState().cart).toEqual(originalCart);
    expect(useCartStore.getState().isLoading).toBe(false);
    expect(useCartStore.getState().error).toBe('Failed to update quantity');
  });

  it('optimistically removes an item before syncing the server cart', async () => {
    const emptyServerCart: Cart = { sellers: [], totalItems: 0, subtotal: 0 };
    let resolveRemove!: () => void;

    vi.mocked(cartApi.removeItem).mockReturnValue(
      new Promise((resolve) => {
        resolveRemove = () => resolve({} as any);
      }) as any,
    );
    vi.mocked(cartApi.getCart).mockResolvedValue({ data: { data: emptyServerCart } } as any);
    useCartStore.setState({ cart: cartWithQuantity(2), isLoading: false, error: null });

    const pending = useCartStore.getState().removeFromCart('buyer-1:variant-1');

    expect(useCartStore.getState().cart).toEqual(emptyServerCart);
    expect(useCartStore.getState().isLoading).toBe(true);

    resolveRemove();
    await pending;

    expect(useCartStore.getState().cart).toEqual(emptyServerCart);
    expect(useCartStore.getState().isLoading).toBe(false);
  });
});
