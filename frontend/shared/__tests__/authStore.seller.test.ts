// @vitest-environment jsdom
import { vi, describe, it, expect, beforeEach } from 'vitest';

// Isolate the store from real axios/cookies. registerSeller decodes the JWT
// `role` claim, so we hand it a token whose payload says SELLER.
vi.mock('../api/auth.api', () => ({ authApi: { registerSeller: vi.fn() } }));
vi.mock('../api/user.api', () => ({ userApi: { getProfile: vi.fn() } }));
vi.mock('../lib/axios', () => ({
  logoutApi: vi.fn(),
  registerAuthFailureHandler: vi.fn(),
}));
vi.mock('js-cookie', () => ({
  default: { set: vi.fn(), get: vi.fn(() => undefined), remove: vi.fn() },
}));

import Cookies from 'js-cookie';
import { authApi } from '../api/auth.api';
import { useAuthStore } from '../store/authStore';

/** Build a fake JWT whose payload contains the given claims. */
function makeJwt(payload: Record<string, unknown>): string {
  const b64 = Buffer.from(JSON.stringify(payload)).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `header.${b64}.sig`;
}

const authResponse = (token: string) => ({
  data: {
    data: {
      accessToken: token,
      refreshToken: 'refresh-xyz',
      userId: 99,
      username: 'shop1',
      email: 's@e.com',
      status: 'ACTIVE',
      role: 'SELLER',
      roles: ['SELLER'],
    },
  },
});

beforeEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ user: null, profile: null, isAuthenticated: false });
  if (typeof window !== 'undefined') {
    delete (window as any).location;
    window.location = new URL('http://localhost/seller') as any;
  }
});

describe('authStore.registerSeller (UC-IDENTITY-006)', () => {
  it('persists tokens, decodes SELLER role and authenticates', async () => {
    (authApi.registerSeller as any).mockResolvedValue(authResponse(makeJwt({ role: 'SELLER' })));

    await useAuthStore.getState().registerSeller({ username: 'shop1', email: 's@e.com', password: 'secret1' });

    const s = useAuthStore.getState();
    expect(s.isAuthenticated).toBe(true);
    expect(s.user?.role).toBe('SELLER');
    expect(s.user?.username).toBe('shop1');
    expect(Cookies.set).toHaveBeenCalledWith('seller_accessToken', expect.any(String), expect.any(Object));
    expect(Cookies.set).toHaveBeenCalledWith('seller_refreshToken', 'refresh-xyz', expect.any(Object));
  });

  it('falls back to SELLER role when the token has no role claim', async () => {
    (authApi.registerSeller as any).mockResolvedValue(authResponse(makeJwt({})));
    await useAuthStore.getState().registerSeller({ username: 'shop1', email: 's@e.com', password: 'secret1' });
    expect(useAuthStore.getState().user?.role).toBe('SELLER');
  });
});
