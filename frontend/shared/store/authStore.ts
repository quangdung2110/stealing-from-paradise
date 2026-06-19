import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { authCookies } from '../utils/cookie';
import { authApi, type RegisterRequest } from '../api/auth.api';
import { userApi, type UserProfileResponse } from '../api/user.api';
import { logoutApi, registerAuthFailureHandler } from '../lib/axios';

export interface AuthUser {
  userId: number;
  username: string;
  email: string;
  phone?: string;
  fullName?: string;
  role: string;
  roles: string[];
  status: string;
  avatarUrl?: string;
}

interface AuthState {
  user: AuthUser | null;
  profile: UserProfileResponse | null;
  isAuthenticated: boolean;
  _hasHydrated: boolean;
  login: (credential: string, password: string) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  registerSeller: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  setHydrated: () => void;
  fetchProfile: () => Promise<void>;
  syncFromAuthResponse: (auth: {
    userId: number;
    username: string;
    email: string;
    phone?: string;
    fullName?: string;
    role: string;
    roles: string[];
    status: string;
    avatarUrl?: string;
  }) => void;
}

export function isAuthFromCookie(): boolean {
  return !!authCookies.get('accessToken');
}

function decodeJwt(token: string): any {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      window.atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      profile: null,
      isAuthenticated: false,
      _hasHydrated: false,

      syncFromAuthResponse: (auth) => {
        set({
          user: {
            userId: auth.userId,
            username: auth.username,
            email: auth.email,
            phone: auth.phone,
            fullName: auth.fullName,
            role: auth.role,
            roles: auth.roles,
            status: auth.status,
            avatarUrl: auth.avatarUrl,
          },
          isAuthenticated: true,
        });
      },

      login: async (credential, password) => {
        const { data } = await authApi.login({ credential, password });
        const auth = data.data!;
        authCookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'lax' });
        if (auth.refreshToken) {
          authCookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'lax' });
        }
        const decoded = decodeJwt(auth.accessToken);
        const role = decoded?.role || 'BUYER';
        set({
          user: {
            userId: auth.userId,
            username: auth.username,
            email: auth.email,
            phone: auth.phone,
            fullName: auth.fullName,
            role: role,
            roles: [role],
            status: auth.status,
            avatarUrl: auth.avatarUrl,
          },
          isAuthenticated: true,
        });
      },

      register: async (req) => {
        const { data } = await authApi.register(req);
        const auth = data.data!;
        authCookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'lax' });
        if (auth.refreshToken) {
          authCookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'lax' });
        }
        const decoded = decodeJwt(auth.accessToken);
        const role = decoded?.role || 'BUYER';
        set({
          user: {
            userId: auth.userId,
            username: auth.username,
            email: auth.email,
            phone: auth.phone,
            fullName: auth.fullName,
            role: role,
            roles: [role],
            status: auth.status,
            avatarUrl: auth.avatarUrl,
          },
          isAuthenticated: true,
        });
      },

      registerSeller: async (req) => {
        const { data } = await authApi.registerSeller(req);
        const auth = data.data!;
        authCookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'lax' });
        if (auth.refreshToken) {
          authCookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'lax' });
        }
        const decoded = decodeJwt(auth.accessToken);
        const role = decoded?.role || 'SELLER';
        set({
          user: {
            userId: auth.userId,
            username: auth.username,
            email: auth.email,
            phone: auth.phone,
            fullName: auth.fullName,
            role: role,
            roles: [role],
            status: auth.status,
            avatarUrl: auth.avatarUrl,
          },
          isAuthenticated: true,
        });
      },

      logout: async () => {
        try { await logoutApi(); } catch (_) {}
        authCookies.remove('accessToken');
        authCookies.remove('refreshToken');
        set({ user: null, profile: null, isAuthenticated: false });
      },

      fetchProfile: async () => {
        const { data } = await userApi.getProfile();
        const profile = data.data!;
        set({ profile });
        if (get().user) {
          set((state) => ({
            user: state.user ? {
              ...state.user,
              username: profile.username,
              email: profile.email,
              phone: profile.phone,
              fullName: profile.fullName,
              roles: profile.roles,
              role: profile.roles?.[0] || state.user.role,
              status: profile.status,
              avatarUrl: profile.avatarUrl,
            } : null,
          }));
        }
      },

      setHydrated: () => set({ _hasHydrated: true }),
    }),
    {
      name: 'auth-store',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (s) => ({ user: s.user }),
      onRehydrateStorage: () => (state) => {
        if (state) {
          state.isAuthenticated = isAuthFromCookie();
          state.setHydrated();
        }
      },
    }
  )
);

// Đăng ký callback để xóa auth state khi axios bị 401/refresh thất bại (tránh circular dependency)
registerAuthFailureHandler(() => {
  useAuthStore.setState({ user: null, profile: null, isAuthenticated: false });
});
