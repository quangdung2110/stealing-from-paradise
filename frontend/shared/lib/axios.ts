import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosError } from 'axios';
import { authCookies } from '../utils/cookie';
import { installMockInterceptor, isMockMode, isNetworkError } from '../api/mock';

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';

// ─── Singleton Axios instance ─────────────────────────────────────
export const apiClient: AxiosInstance = axios.create({
  baseURL: `${BASE_URL}`,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

// ─── Install mock interceptor ─────────────────────────────────────
installMockInterceptor(apiClient);
if (isMockMode()) {
  console.warn(
    '[flashsale] MOCK MODE đang bật — mọi API trả dữ liệu giả. ' +
    'Set VITE_API_URL (hoặc bỏ VITE_BACKEND_MODE=mock) để gọi backend thật.'
  );
}

// ─── Request interceptor: gắn Access Token ───────────────────────
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (isMockMode()) return config;
    const token = authCookies.get('accessToken');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ─── Response interceptor: auto-refresh token khi 401 ───────────
let isRefreshing = false;
let failedQueue: Array<{ resolve: (v: unknown) => void; reject: (r: unknown) => void }> = [];

const processQueue = (error: AxiosError | null, token: string | null = null) => {
  failedQueue.forEach((p) => error ? p.reject(error) : p.resolve(token));
  failedQueue = [];
};

// Axios instance for refresh token calls — uses raw axios so it bypasses this interceptor entirely
const rawAxios = axios.create();

const REFRESH_ERROR_CODES = new Set(['AUTH_003', 'AUTH_002']);

// ─── Callback handler to clear state without circular dependency ───
let onAuthFailure: (() => void) | null = null;
let isRedirectingToLogin = false;

export function registerAuthFailureHandler(handler: () => void) {
  onAuthFailure = handler;
}

export function handleAuthFailure() {
  const isAtLogin = window.location.pathname === '/login';
  if (!isRedirectingToLogin && !isAtLogin) {
    isRedirectingToLogin = true;
    authCookies.remove('accessToken');
    authCookies.remove('refreshToken');
    if (onAuthFailure) {
      onAuthFailure();
    }
    window.location.href = '/login';
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if ((error as any)?.isMockResponse) {
      return (error as any).response;
    }
    if (isNetworkError(error)) {
      return Promise.reject(error);
    }

    const originalRequest = error?.config as InternalAxiosRequestConfig & { _retry?: boolean };
    const status = error?.response?.status;
    const errorCode = (error?.response?.data as any)?.errorCode;

    // Bỏ qua nếu là request liên quan đến auth (đăng nhập, đăng ký, refresh, logout) để tránh loop hoặc reload trang khi gõ sai mật khẩu
    const isAuthRequest = originalRequest?.url?.includes('/auth/');
    if (isAuthRequest) {
      return Promise.reject(error);
    }

    // 401 → attempt token refresh
    if (status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshToken = authCookies.get('refreshToken');
        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        const { data } = await rawAxios.post<{ data: { accessToken: string; refreshToken?: string } }>(
          `${BASE_URL}/auth/refresh`,
          {},
          {
            headers: { Authorization: `Bearer ${refreshToken}` },
            withCredentials: true,
          }
        );
        const newToken = data.data.accessToken;
        const newRefreshToken = data.data.refreshToken;
        authCookies.set('accessToken', newToken, { secure: true, sameSite: 'lax' });
        if (newRefreshToken) {
          authCookies.set('refreshToken', newRefreshToken, { secure: true, sameSite: 'lax' });
        }
        processQueue(null, newToken);
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError as AxiosError, null);
        handleAuthFailure();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // 401 from refresh endpoint itself → token truly invalid, logout
    if (status === 401 && originalRequest._retry && REFRESH_ERROR_CODES.has(errorCode)) {
      handleAuthFailure();
    }

    return Promise.reject(error);
  }
);

// ─── Logout: bypass interceptor to avoid 401→refresh→loop ───────
export async function logoutApi(): Promise<void> {
  const accessToken = authCookies.get('accessToken');
  await rawAxios.post(
    `${BASE_URL}/auth/logout`,
    {},
    {
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      withCredentials: true,
    }
  );
}

export default apiClient;
