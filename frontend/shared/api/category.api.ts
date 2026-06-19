import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

// ─── Category Types ──────────────────────────────────────────────────────────

export interface Category {
  categoryId: string;
  name: string;
  slug: string;
  parentId?: string | null;
  level: number;
  description?: string;
  productCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateCategoryRequest {
  name: string;
  slug: string;
  parentId?: string | null;
  level: number;
  description?: string;
}

export interface UpdateCategoryRequest {
  name?: string;
  slug?: string;
  parentId?: string | null;
  level?: number;
  description?: string;
}

// Helper to map mock backend response 'id' to 'categoryId'
const mapCategory = (c: any): Category => {
  if (!c) return c;
  return {
    ...c,
    categoryId: c.categoryId || c.id || '',
    parentId: c.parentId || null,
  };
};

// ─── Public API ──────────────────────────────────────────────────────────────

export const categoryApi = {
  /** Get list of all categories */
  getCategories: async () => {
    const response = await apiClient.get<ApiResponse<Category[]>>('/categories');
    if (response.data && Array.isArray(response.data.data)) {
      response.data.data = response.data.data.map(mapCategory);
    }
    return response;
  },
};

// ─── Admin API ───────────────────────────────────────────────────────────────

export const adminCategoryApi = {
  /** Create a new category */
  create: async (body: CreateCategoryRequest) => {
    const response = await apiClient.post<ApiResponse<Category>>('/admin/categories', body);
    if (response.data && response.data.data) {
      response.data.data = mapCategory(response.data.data);
    }
    return response;
  },

  /** Update an existing category */
  update: async (categoryId: string, body: UpdateCategoryRequest) => {
    const response = await apiClient.put<ApiResponse<Category>>(`/admin/categories/${categoryId}`, body);
    if (response.data && response.data.data) {
      response.data.data = mapCategory(response.data.data);
    }
    return response;
  },

  /** Delete a category */
  delete: (categoryId: string) =>
    apiClient.delete<ApiResponse<void>>(`/admin/categories/${categoryId}`),
};
