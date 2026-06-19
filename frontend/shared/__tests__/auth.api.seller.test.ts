import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../lib/axios', () => {
  const m = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() };
  return { default: m, apiClient: m };
});

import apiClient from '../lib/axios';
import { authApi } from '../api/auth.api';

const client = apiClient as unknown as Record<'post', ReturnType<typeof vi.fn>>;

beforeEach(() => {
  vi.clearAllMocks();
  client.post.mockResolvedValue(Promise.resolve({ data: { success: true, data: {} } }));
});

describe('authApi — seller-relevant', () => {
  it('registerSeller → POST /auth/register/seller with body (UC-IDENTITY-006)', async () => {
    const body = { username: 'shop1', email: 's@e.com', password: 'secret1' };
    await authApi.registerSeller(body);
    expect(client.post).toHaveBeenCalledWith('/auth/register/seller', body);
  });

  it('login → POST /auth/login with credential/password', async () => {
    const body = { credential: 'shop1', password: 'secret1' };
    await authApi.login(body);
    expect(client.post).toHaveBeenCalledWith('/auth/login', body);
  });
});
