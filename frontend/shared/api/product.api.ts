import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface ProductVariant {
  variantId: string;
  skuCode: string;
  variantName: string;
  stockQuantity: number;
  stock?: number;
  isFlash: boolean;
  price?: number;
  originalPrice?: number;
}

/** Matches backend ProductResponse: GET /products/{productId} */
export interface ProductDetail {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
  description?: string;
  categoryId?: string;
  categoryName?: string;
  categorySlug?: string;
  attributes?: Record<string, unknown>;
  images?: string[];
  status?: string;
  rejectReason?: string;
  price?: number;
  originalPrice?: number;
  stockAvailable?: number;
  variants?: ProductVariant[];
  rating?: number;
  reviewsCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** Backend product-service ProductResponse shape: GET /products/{productId} */
interface RawVariantResponse {
  id: string;
  productId: string;
  /** Backend VariantResponse serializes variantCode as "skuCode" (@see VariantResponse.java) */
  skuCode: string;
  variantName: string;
  variantAttributes?: Record<string, unknown>;
  price: number;
  originalPrice?: number;
  stockQuantity: number;
  isFlash?: boolean;
  status: string;
  imageUrl?: string;
}

interface RawProductResponse {
  id: string;
  name: string;
  slug?: string;
  description?: string;
  categoryId?: string;
  categoryName?: string;
  sellerId: number;
  sellerName?: string;
  status?: string;
  attributes?: Record<string, unknown>;
  variants?: RawVariantResponse[];
  /** Backend returns string[] (urls); mock may return object[] backward compat */
  images?: string[] | { url: string; sortOrder?: number; variantId?: string | null }[];
  rejectReason?: string;
  createdAt?: string;
  updatedAt?: string;
  fsItemId?: number;
}

function mapProductDetail(raw: RawProductResponse): ProductDetail {
  const activeVariants = (raw.variants ?? []).filter(v => v.status === 'ACTIVE');
  const cheapest = activeVariants.length
    ? activeVariants.reduce((min, v) => (v.price < min.price ? v : min))
    : undefined;
  // Backend returns images as string[] (urls via List<String> in ProductResponse).
  // Frontend also accepts object[] for mock data backward compat.
  const rawImages = raw.images ?? [];
  const images: string[] = rawImages.length > 0 && typeof rawImages[0] === 'string'
    ? (rawImages as string[])
    : (rawImages as { url: string; sortOrder?: number }[])
        .slice()
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
        .map(img => img.url);
  return {
    productId: raw.id,
    sellerId: raw.sellerId,
    sellerName: raw.sellerName,
    name: raw.name,
    description: raw.description,
    categoryId: raw.categoryId,
    categoryName: raw.categoryName,
    attributes: raw.attributes,
    images,
    status: raw.status,
    rejectReason: raw.rejectReason,
    price: cheapest?.price,
    originalPrice: cheapest?.originalPrice,
    variants: activeVariants.map(v => ({
      variantId: v.id,
      skuCode: v.skuCode,
      variantName: v.variantName,
      stockQuantity: v.stockQuantity ?? 0,
      stock: v.stockQuantity ?? 0,
      isFlash: v.isFlash ?? (v.originalPrice != null && v.price < v.originalPrice),
      price: v.price,
      originalPrice: v.originalPrice,
    })),
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
  };
}

/** Matches backend ProductCard (used in product grids from search service) */
export interface ProductListItem {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
  price?: number;
  originalPrice?: number;
  categoryName?: string;
  images?: string[];
  stock?: number;
  stockAvailable?: number;
  rating?: number;
  reviewsCount?: number;
  isFlash?: boolean;
  createdAt?: string;
}

/** Backend SearchService ProductCard shape */
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

interface SearchResponse {
  totalResults: number;
  page: number;
  size: number;
  totalPages: number;
  products: SearchProductCard[];
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
    stockAvailable: card.stockAvailable,
    isFlash: card.isFlash,
  };
}

/** Variant lookup by SKU: GET /products/variants/sku/{skuCode} */
export interface VariantBySku {
  id: string;
  variantCode: string;
  variantName: string;
  imageUrl?: string;
  productId: string;
  productName: string;
  sellerId: number;
  price: number;
}

export const productApi = {
  /**
   * List products via search service (the only product listing endpoint).
   * Params: search → q
   */
  getProducts: async (params?: {
    search?: string;
    priceMin?: number;
    priceMax?: number;
    inStock?: boolean;
    isFlash?: boolean;
    sellerId?: number;
    page?: number;
    size?: number;
    sort?: string;
  }) => {
    const res = await apiClient.get<ApiResponse<SearchResponse>>('/search/products', {
      params: {
        q: params?.search || undefined,
        price_min: params?.priceMin,
        price_max: params?.priceMax,
        in_stock: params?.inStock,
        is_flash: params?.isFlash,
        seller_id: params?.sellerId,
        page: params?.page ?? 0,
        size: params?.size ?? 20,
        sort: params?.sort || undefined,
      },
    });
    const body = res.data.data;
    return {
      ...res,
      data: {
        ...res.data,
        data: {
          content: (body?.products ?? []).map(mapProductCard),
          totalElements: body?.totalResults ?? 0,
          totalPages: body?.totalPages ?? 0,
        },
      },
    };
  },

  /** Look up a single variant (with product name/image/price) by its SKU code. */
  getVariantBySku: (skuCode: string) =>
    apiClient
      .get<ApiResponse<VariantBySku>>(`/products/variants/sku/${encodeURIComponent(skuCode)}`)
      .then(res => res.data.data),

  /** Get product by ID — maps backend ProductResponse to ProductDetail */
  getProductById: (productId: string) =>
    apiClient.get<ApiResponse<RawProductResponse>>(`/products/${productId}`).then(res => ({
      ...res,
      data: {
        ...res.data,
        data: res.data.data ? mapProductDetail(res.data.data) : undefined,
      } as ApiResponse<ProductDetail>,
    })),

  /** Get search suggestions (autocomplete) — debounced while typing */
  getSuggestions: (query: string, size: number = 5) =>
    apiClient.get<ApiResponse<{ suggestions: string[] }>>('/search/products/suggest', {
      params: { q: query, size },
    }).then(res => res.data.data?.suggestions ?? []),

  /** Search products (delegates to search service) */
  searchProducts: (query: string, params?: {
    priceMin?: number;
    priceMax?: number;
    inStock?: boolean;
    isFlash?: boolean;
    sellerId?: number;
    page?: number;
    size?: number;
    sort?: string;
  }) =>
    apiClient.get<ApiResponse<SearchResponse>>('/search/products', {
      params: {
        q: query,
        price_min: params?.priceMin,
        price_max: params?.priceMax,
        in_stock: params?.inStock,
        is_flash: params?.isFlash,
        seller_id: params?.sellerId,
        page: params?.page ?? 0,
        size: params?.size ?? 20,
        sort: params?.sort || undefined,
      },
    }).then(res => ({
      ...res,
      data: {
        ...res.data,
        data: {
          content: (res.data.data?.products ?? []).map(mapProductCard),
          totalElements: res.data.data?.totalResults ?? 0,
          totalPages: res.data.data?.totalPages ?? 0,
        },
      },
    })),
};
