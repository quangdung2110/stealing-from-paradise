import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../lib/axios', () => {
  const m = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() };
  return { default: m, apiClient: m };
});

import apiClient from '../lib/axios';
import { orderApi } from '../api/order.api';

const client = apiClient as unknown as Record<'get' | 'post' | 'put' | 'delete', ReturnType<typeof vi.fn>>;
const ok = (data: any = {}) => Promise.resolve({ data: { success: true, data } });

beforeEach(() => {
  vi.clearAllMocks();
  client.get.mockResolvedValue(ok());
  client.post.mockResolvedValue(ok());
  client.put.mockResolvedValue(ok());
});

describe('orderApi — seller methods', () => {
  it('getSellerOrders → GET /sellers/me/orders with status/page/size params', async () => {
    await orderApi.getSellerOrders({ status: 'PAID', page: 2, size: 20 });
    expect(client.get).toHaveBeenCalledWith('/sellers/me/orders', {
      params: { status: 'PAID', page: 2, size: 20 },
    });
  });

  it('getOrderById → GET /orders/{id}', async () => {
    await orderApi.getOrderById(42);
    expect(client.get).toHaveBeenCalledWith('/orders/42');
  });

  it('cancelOrder → POST /orders/{id}/cancel with {reason,note} (UC-ORDER-008)', async () => {
    await orderApi.cancelOrder(42, { reason: 'Hết hàng không thể giao', note: 'xin lỗi' });
    expect(client.post).toHaveBeenCalledWith('/orders/42/cancel', {
      reason: 'Hết hàng không thể giao',
      note: 'xin lỗi',
    });
  });

  it('updateTracking → PUT /orders/{id}/tracking with body (UC-ORDER-004)', async () => {
    await orderApi.updateTracking(42, { trackingNumber: 'VN1', carrier: 'GHN', note: 'x' });
    expect(client.put).toHaveBeenCalledWith('/orders/42/tracking', {
      trackingNumber: 'VN1', carrier: 'GHN', note: 'x',
    });
  });

  it('returnToSender → POST /orders/{id}/return-to-sender as multipart FormData (UC-ORDER-006 B)', async () => {
    const fd = new FormData();
    fd.append('return_tracking_number', 'RET-1');
    await orderApi.returnToSender(42, fd);
    expect(client.post).toHaveBeenCalledWith(
      '/orders/42/return-to-sender',
      fd,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
  });
});
