import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../lib/axios', () => {
  const m = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() };
  return { default: m, apiClient: m };
});

import apiClient from '../lib/axios';
import { paymentApi } from '../api/payment.api';

const client = apiClient as unknown as Record<'get', ReturnType<typeof vi.fn>>;

beforeEach(() => {
  vi.clearAllMocks();
  client.get.mockResolvedValue(Promise.resolve({ data: { success: true, data: {} } }));
});

describe('paymentApi', () => {
  it('getPayment → GET /payments/parent-order/{id}', async () => {
    await paymentApi.getPayment(7);
    expect(client.get).toHaveBeenCalledWith('/payments/parent-order/7');
  });

  it('getClientSecret → GET /payments/client-secret/{id}', async () => {
    await paymentApi.getClientSecret(7);
    expect(client.get).toHaveBeenCalledWith('/payments/client-secret/7');
  });
});
