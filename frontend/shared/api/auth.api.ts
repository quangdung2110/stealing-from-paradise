import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface LoginRequest {
  credential: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  phone?: string;
  password: string;
  fullName?: string;
}

/** Matches backend AuthResponse DTO */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  refreshExpiresIn: number;
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

export const authApi = {
  login: (body: LoginRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/login', body),

  register: (body: RegisterRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/register', body),

  registerSeller: (body: RegisterRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/register/seller', body),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/auth/logout'),

  refresh: () =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/refresh'),

  getProfile: () =>
    apiClient.get<ApiResponse<unknown>>('/users/me'),
};
