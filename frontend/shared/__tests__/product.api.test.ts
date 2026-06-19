import { describe, it, expect } from 'vitest';

// Inline mapProductCard mirroring product.api.ts
interface SearchProductCard {
  productId: string;
  name: string;
  sellerId: number;
  sellerName: string;
  categoryId: string;
  categoryName: string;
  priceMin: number | null;
  priceMax: number | null;
  images: string[];
  stockAvailable: number;
  isFlash: boolean;
  thumbnailUrl: string;
}

interface ProductListItem {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
  price?: number;
  originalPrice?: number;
  categoryName?: string;
  images?: string[];
  stock?: number;
  rating?: number;
  reviewsCount?: number;
  isFlash?: boolean;
  createdAt?: string;
}

function mapProductCard(card: SearchProductCard): ProductListItem {
  return {
    productId: card.productId,
    sellerId: card.sellerId,
    sellerName: card.sellerName,
    name: card.name,
    price: card.priceMin ?? undefined,
    originalPrice: card.priceMax ?? undefined,
    categoryName: card.categoryName,
    images: card.images,
    stock: card.stockAvailable,
    isFlash: card.isFlash,
  };
}

const mockCard: SearchProductCard = {
  productId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
  name: 'Test Product',
  sellerId: 10,
  sellerName: 'Test Seller',
  categoryId: 'cat-1',
  categoryName: 'Electronics',
  priceMin: 299000,
  priceMax: 499000,
  images: ['/img/1.jpg', '/img/2.jpg'],
  stockAvailable: 50,
  isFlash: true,
  thumbnailUrl: '/thumb.jpg',
};

describe('mapProductCard', () => {
  it('maps productId, sellerId, sellerName, name', () => {
    const result = mapProductCard(mockCard);
    expect(result.productId).toBe('a1b2c3d4-e5f6-7890-abcd-ef1234567890');
    expect(result.sellerId).toBe(10);
    expect(result.sellerName).toBe('Test Seller');
    expect(result.name).toBe('Test Product');
  });

  it('maps priceMin → price and priceMax → originalPrice', () => {
    const result = mapProductCard(mockCard);
    expect(result.price).toBe(299000);
    expect(result.originalPrice).toBe(499000);
  });

  it('maps null priceMin to undefined', () => {
    const result = mapProductCard({ ...mockCard, priceMin: null });
    expect(result.price).toBeUndefined();
  });

  it('maps null priceMax to undefined', () => {
    const result = mapProductCard({ ...mockCard, priceMax: null });
    expect(result.originalPrice).toBeUndefined();
  });

  it('maps categoryName and images', () => {
    const result = mapProductCard(mockCard);
    expect(result.categoryName).toBe('Electronics');
    expect(result.images).toEqual(['/img/1.jpg', '/img/2.jpg']);
  });

  it('maps stockAvailable → stock and isFlash', () => {
    const result = mapProductCard(mockCard);
    expect(result.stock).toBe(50);
    expect(result.isFlash).toBe(true);
  });

  it('handles empty images array', () => {
    const result = mapProductCard({ ...mockCard, images: [] });
    expect(result.images).toEqual([]);
  });

  it('handles zero stock', () => {
    const result = mapProductCard({ ...mockCard, stockAvailable: 0, isFlash: false });
    expect(result.stock).toBe(0);
    expect(result.isFlash).toBe(false);
  });
});
