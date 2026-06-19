import { vi, describe, it, expect, beforeEach } from 'vitest';

// Mock the axios client so we assert verb + URL + params/body without any
// network, interceptors, or cookies. The api module imports the default export.
vi.mock('../lib/axios', () => {
  const m = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() };
  return { default: m, apiClient: m };
});

import apiClient from '../lib/axios';
import { sellerApi } from '../api/seller.api';

const client = apiClient as unknown as Record<'get' | 'post' | 'put' | 'delete', ReturnType<typeof vi.fn>>;
const ok = (data: any = {}) => Promise.resolve({ data: { success: true, data } });

beforeEach(() => {
  vi.clearAllMocks();
  client.get.mockResolvedValue(ok());
  client.post.mockResolvedValue(ok());
  client.put.mockResolvedValue(ok());
  client.delete.mockResolvedValue(ok());
});

describe('sellerApi — dashboard & stripe onboarding', () => {
  it('getDashboardStats → GET /sellers/me/dashboard', async () => {
    await sellerApi.getDashboardStats();
    expect(client.get).toHaveBeenCalledWith('/sellers/me/dashboard');
  });

  it('startStripeOnboarding → POST /stripe/onboarding/start', async () => {
    await sellerApi.startStripeOnboarding();
    expect(client.post).toHaveBeenCalledWith('/stripe/onboarding/start');
  });

  it('getStripeStatus → GET /stripe/onboarding/status', async () => {
    await sellerApi.getStripeStatus();
    expect(client.get).toHaveBeenCalledWith('/stripe/onboarding/status');
  });

  it('refreshStripeLink → POST /stripe/onboarding/refresh-link', async () => {
    await sellerApi.refreshStripeLink();
    expect(client.post).toHaveBeenCalledWith('/stripe/onboarding/refresh-link');
  });
});

describe('sellerApi — product lifecycle', () => {
  it('submitForReview → POST /seller/products/{id}/submit', async () => {
    await sellerApi.submitForReview('p1');
    expect(client.post).toHaveBeenCalledWith('/seller/products/p1/submit');
  });

  it('publishProduct → POST /seller/products/{id}/publish', async () => {
    await sellerApi.publishProduct('p2');
    expect(client.post).toHaveBeenCalledWith('/seller/products/p2/publish');
  });

  it('unpublishProduct → POST /seller/products/{id}/unpublish', async () => {
    await sellerApi.unpublishProduct('p3');
    expect(client.post).toHaveBeenCalledWith('/seller/products/p3/unpublish');
  });

  it('createProduct → POST /products with body', async () => {
    const body = { name: 'A', description: 'd', categoryId: 'c1', price: 1000, stock: 5 };
    await sellerApi.createProduct(body);
    expect(client.post).toHaveBeenCalledWith('/products', {
      name: 'A',
      description: 'd',
      categoryId: 'c1',
      attributes: undefined,
    });
  });

  it('updateProduct → PUT /products/{id} with body', async () => {
    const body = { name: 'A2', description: 'd2', categoryId: 'c1', images: ['ignored.png'] };
    await sellerApi.updateProduct('p4', body);
    expect(client.put).toHaveBeenCalledWith('/products/p4', {
      name: 'A2',
      description: 'd2',
      categoryId: 'c1',
      attributes: undefined,
    });
  });

  it('deleteProduct → DELETE /seller/products/{id}', async () => {
    await sellerApi.deleteProduct('p5');
    expect(client.delete).toHaveBeenCalledWith('/seller/products/p5');
  });
});

describe('sellerApi — variants', () => {
  it('getVariants → GET /seller/products/{id}/variants', async () => {
    client.get.mockResolvedValueOnce(ok([{ id: 'v1', variantCode: 'SKU1', variantName: 'V1', price: 100, stockQuantity: 7 }]));
    const res = await sellerApi.getVariants('p1');
    expect(client.get).toHaveBeenCalledWith('/seller/products/p1/variants');
    expect(res.data.data?.[0]).toEqual(expect.objectContaining({ skuCode: 'SKU1', stock: 7 }));
  });

  it('createVariant → POST /seller/products/{id}/variants with body', async () => {
    const body = { skuCode: 'SKU1', variantName: 'V1', price: 100, stock: 10 };
    await sellerApi.createVariant('p1', body);
    expect(client.post).toHaveBeenCalledWith('/seller/products/p1/variants', {
      variantCode: 'SKU1',
      variantName: 'V1',
      price: 100,
      originalPrice: undefined,
      stockQuantity: 10,
      imageUrl: undefined,
    });
  });

  it('updateVariant → PUT /seller/variants/{id} with body', async () => {
    await sellerApi.updateVariant('v9', { price: 200, stock: 4 });
    expect(client.put).toHaveBeenCalledWith('/seller/variants/v9', {
      variantName: undefined,
      price: 200,
      originalPrice: undefined,
      stockQuantity: 4,
      status: undefined,
      imageUrl: undefined,
      version: undefined,
    });
  });

  it('deleteVariant → DELETE /seller/variants/{id}', async () => {
    await sellerApi.deleteVariant('v9');
    expect(client.delete).toHaveBeenCalledWith('/seller/variants/v9');
  });
});

describe('sellerApi — inventory', () => {
  it('adjustInventory → POST /seller/inventory/adjust with body', async () => {
    const body = { skuCode: 'SKU1', delta: -3, reason: 'Hỏng' };
    await sellerApi.adjustInventory(body);
    expect(client.post).toHaveBeenCalledWith('/seller/inventory/adjust', {
      delta: -3,
      reason: 'MANUAL',
    }, {
      params: { skuCode: 'SKU1' },
    });
  });

  it('getInventoryLogs → GET /seller/inventory/{sku}/logs', async () => {
    await sellerApi.getInventoryLogs('SKU1');
    expect(client.get).toHaveBeenCalledWith('/seller/inventory/SKU1/logs');
  });

  it('restockInventory → PUT /inventory/{sku}/restock with body', async () => {
    const body = { quantity: 50, reason: 'Nhập hàng' };
    await sellerApi.restockInventory('SKU1', body);
    expect(client.put).toHaveBeenCalledWith('/inventory/SKU1/restock', { quantity: 50 });
  });
});

describe('sellerApi — images & earnings', () => {
  it('getPresignedUrl → GET /products/{id}/presigned-url with filename & contentType params', async () => {
    await sellerApi.getPresignedUrl('p1', 'a.png', 'image/png');
    expect(client.get).toHaveBeenCalledWith('/products/p1/presigned-url', {
      params: { filename: 'a.png', contentType: 'image/png' },
    });
  });

  it('getEarnings → GET /seller/payments/earnings', async () => {
    await sellerApi.getEarnings();
    expect(client.get).toHaveBeenCalledWith('/seller/payments/earnings');
  });

  it('getStripeDashboardLink → GET /seller/payments/stripe-dashboard', async () => {
    await sellerApi.getStripeDashboardLink();
    expect(client.get).toHaveBeenCalledWith('/seller/payments/stripe-dashboard');
  });
});
