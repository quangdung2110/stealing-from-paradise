import { describe, it, expect, beforeEach } from 'vitest';
import { useOrderStore } from '../store/orderStore';

describe('orderStore', () => {
  beforeEach(() => {
    useOrderStore.setState({
      orders: [],
      currentOrder: null,
      currentParentOrder: null,
      checkoutResult: null,
      sellerOrders: [],
      isLoading: false,
      error: null,
      pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
  });

  describe('initial state', () => {
    it('starts with empty orders', () => {
      expect(useOrderStore.getState().orders).toEqual([]);
    });

    it('starts with null currentOrder, currentParentOrder, checkoutResult', () => {
      const s = useOrderStore.getState();
      expect(s.currentOrder).toBeNull();
      expect(s.currentParentOrder).toBeNull();
      expect(s.checkoutResult).toBeNull();
    });

    it('starts with empty sellerOrders', () => {
      expect(useOrderStore.getState().sellerOrders).toEqual([]);
    });

    it('starts with not loading and no error', () => {
      expect(useOrderStore.getState().isLoading).toBe(false);
      expect(useOrderStore.getState().error).toBeNull();
    });
  });

  describe('clearCheckoutResult', () => {
    it('sets checkoutResult to null', () => {
      useOrderStore.setState({
        checkoutResult: {
          parentOrderId: 100,
          orderCode: 'ORD-001',
          orders: [],
          totalAmount: 500,
          finalAmount: 500,
          itemsCount: 2,
          createdAt: '2026-05-28T00:00:00Z',
        },
      });

      useOrderStore.getState().clearCheckoutResult();
      expect(useOrderStore.getState().checkoutResult).toBeNull();
    });
  });

  describe('clearCurrentOrder', () => {
    it('sets currentOrder to null', () => {
      useOrderStore.setState({
        currentOrder: {
          orderId: 1,
          parentOrderId: 100,
          orderCode: 'SUB-001',
          sellerId: 10,
          sellerName: 'Shop A',
          status: 'PENDING',
          totalAmt: 200,
          finalAmt: 200,
          createdAt: '2026-05-28T00:00:00Z',
        },
      });

      useOrderStore.getState().clearCurrentOrder();
      expect(useOrderStore.getState().currentOrder).toBeNull();
    });
  });

  describe('clearCurrentParentOrder', () => {
    it('sets currentParentOrder to null', () => {
      useOrderStore.setState({
        currentParentOrder: {
          parentOrderId: 100,
          orderCode: 'ORD-001',
          status: 'PENDING',
          totalAmt: 500,
          finalAmt: 500,
          orders: [],
          createdAt: '2026-05-28T00:00:00Z',
        },
      });

      useOrderStore.getState().clearCurrentParentOrder();
      expect(useOrderStore.getState().currentParentOrder).toBeNull();
    });
  });

  describe('orders list', () => {
    it('holds multiple orders', () => {
      useOrderStore.setState({
        orders: [
          { orderId: 1, parentOrderId: 100, orderCode: 'SUB-001', sellerId: 10, sellerName: 'Shop A', status: 'PENDING', totalAmt: 200, finalAmt: 200, itemCount: 1, createdAt: '2026-05-28T00:00:00Z' },
          { orderId: 2, parentOrderId: 100, orderCode: 'SUB-002', sellerId: 20, sellerName: 'Shop B', status: 'SHIPPING', totalAmt: 300, finalAmt: 300, itemCount: 2, createdAt: '2026-05-28T00:00:00Z' },
        ],
      });

      const orders = useOrderStore.getState().orders;
      expect(orders).toHaveLength(2);
      expect(orders[0].orderCode).toBe('SUB-001');
      expect(orders[1].status).toBe('SHIPPING');
    });
  });

  describe('error state', () => {
    it('exposes error', () => {
      useOrderStore.setState({ error: 'Checkout failed' });
      expect(useOrderStore.getState().error).toBe('Checkout failed');
    });
  });
});
