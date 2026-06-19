import { describe, it, expect, beforeEach } from 'vitest';
import { useProductStore } from '../store/productStore';
import type { ProductListItem, ProductDetail } from '../api/product.api';

const mockProduct: ProductListItem = {
  productId: 'prod-1',
  sellerId: 1,
  sellerName: 'Shop A',
  name: 'Test Product',
  price: 100,
  originalPrice: 150,
  categoryName: 'Electronics',
  stock: 10,
  isFlash: false,
};

const mockProduct2: ProductListItem = {
  productId: 'prod-2',
  sellerId: 2,
  sellerName: 'Shop B',
  name: 'Another Product',
  price: 200,
  stock: 5,
};

const mockDetail: ProductDetail = {
  productId: 'prod-1',
  sellerId: 1,
  sellerName: 'Shop A',
  name: 'Test Product Detail',
  description: 'Full description here',
  price: 100,
  originalPrice: 150,
  categoryId: 'cat-1',
  categoryName: 'Electronics',
  stockAvailable: 10,
  isFlash: false,
};

describe('productStore', () => {
  beforeEach(() => {
    useProductStore.setState({
      products: [],
      currentProduct: null,
      isLoading: false,
      error: null,
      pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      filters: {},
    });
  });

  describe('initial state', () => {
    it('starts with empty products array', () => {
      expect(useProductStore.getState().products).toEqual([]);
    });

    it('starts with null currentProduct', () => {
      expect(useProductStore.getState().currentProduct).toBeNull();
    });

    it('starts with not loading and no error', () => {
      expect(useProductStore.getState().isLoading).toBe(false);
      expect(useProductStore.getState().error).toBeNull();
    });

    it('starts with default pagination', () => {
      expect(useProductStore.getState().pagination).toEqual({
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });
    });
  });

  describe('clearCurrentProduct', () => {
    it('sets currentProduct to null', () => {
      useProductStore.setState({ currentProduct: mockDetail });
      expect(useProductStore.getState().currentProduct).not.toBeNull();

      useProductStore.getState().clearCurrentProduct();
      expect(useProductStore.getState().currentProduct).toBeNull();
    });

    it('does not affect products list', () => {
      useProductStore.setState({
        products: [mockProduct],
        currentProduct: mockDetail,
      });

      useProductStore.getState().clearCurrentProduct();
      expect(useProductStore.getState().products).toEqual([mockProduct]);
    });
  });

  describe('setFilters', () => {
    it('sets a single filter', () => {
      useProductStore.getState().setFilters({ category: 'Electronics' });
      expect(useProductStore.getState().filters).toEqual({ category: 'Electronics' });
    });

    it('merges multiple filter calls', () => {
      const store = useProductStore.getState();
      store.setFilters({ category: 'Electronics' });
      store.setFilters({ search: 'phone' });
      expect(useProductStore.getState().filters).toEqual({
        category: 'Electronics',
        search: 'phone',
      });
    });

    it('overwrites existing filter key', () => {
      useProductStore.getState().setFilters({ sort: 'asc' });
      useProductStore.getState().setFilters({ sort: 'desc' });
      expect(useProductStore.getState().filters).toEqual({ sort: 'desc' });
    });

    it('clearing other state does not affect filters', () => {
      useProductStore.getState().setFilters({ search: 'test' });
      useProductStore.getState().clearCurrentProduct();
      expect(useProductStore.getState().filters).toEqual({ search: 'test' });
    });
  });

  describe('product list state', () => {
    it('holds products array', () => {
      useProductStore.setState({ products: [mockProduct, mockProduct2] });
      const state = useProductStore.getState();
      expect(state.products).toHaveLength(2);
      expect(state.products[0].name).toBe('Test Product');
      expect(state.products[1].name).toBe('Another Product');
    });

    it('pagination reflects state', () => {
      useProductStore.setState({
        pagination: { page: 1, size: 10, totalElements: 42, totalPages: 5 },
      });
      const p = useProductStore.getState().pagination;
      expect(p.page).toBe(1);
      expect(p.size).toBe(10);
      expect(p.totalElements).toBe(42);
      expect(p.totalPages).toBe(5);
    });

    it('error state is exposed', () => {
      useProductStore.setState({ error: 'Something went wrong' });
      expect(useProductStore.getState().error).toBe('Something went wrong');
    });
  });
});
