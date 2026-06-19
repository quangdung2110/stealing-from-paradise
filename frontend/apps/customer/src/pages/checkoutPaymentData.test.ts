import { describe, expect, it } from 'vitest';
import {
  buildCheckoutPaymentData,
  formatPaymentCountdown,
  getPaymentDeadlineAt,
  getPaymentRemainingSeconds,
  normalizeCheckoutPaymentData,
  type CheckoutPaymentData,
} from './checkoutPaymentData';
import type { CheckoutPreviewResponse, CheckoutSubmitResponse } from '@shared/api/cart.api';
import type { ParentOrderDetail } from '@shared/api/order.api';

const preview: CheckoutPreviewResponse = {
  previewToken: 'preview-1',
  expiresAt: '2026-06-12T13:00:00.000Z',
  customerId: 10,
  totalItems: 2,
  totalAmount: 300000,
  allValid: true,
  sellers: [
    {
      sellerId: 42,
      sellerName: 'Shop A',
      subtotal: 300000,
      items: [
        {
          variantId: 'variant-1',
          skuCode: 'SKU-1',
          productName: 'Product 1',
          variantName: 'Default',
          priceSnapshot: 150000,
          quantity: 2,
          subtotal: 300000,
          sellerId: 42,
        },
      ],
    },
  ],
};

const submit: CheckoutSubmitResponse = {
  sessionId: 'session-1',
  parentOrderId: 123,
  createdAt: '2026-06-12T13:00:00.000Z',
  totalItems: 2,
  totalAmount: 300000,
  message: 'accepted',
};

describe('checkout payment data', () => {
  it('builds display orders from checkout preview when submit has no orders', () => {
    const data = buildCheckoutPaymentData(submit, preview);

    expect(data.parentOrderId).toBe(123);
    expect(data.finalAmount).toBe(300000);
    expect(data.timeoutAt).toBe('2026-06-12T13:05:00.000Z');
    expect(data.orders).toHaveLength(1);
    expect(data.orders?.[0]).toMatchObject({
      sellerId: 42,
      sellerName: 'Shop A',
      finalAmt: 300000,
      itemCount: 2,
    });
  });

  it('prefers parent order details when they are available', () => {
    const parentOrder: ParentOrderDetail = {
      parentOrderId: 456,
      orderCode: 'PO-456',
      status: 'PENDING',
      totalAmt: 300000,
      finalAmt: 300000,
      createdAt: '2026-06-12T13:00:02.000Z',
      orders: [
        {
          orderId: 99,
          parentOrderId: 456,
          orderCode: 'OR-99',
          sellerId: 42,
          sellerName: 'Shop A',
          status: 'PENDING',
          totalAmt: 300000,
          finalAmt: 300000,
          itemCount: 2,
          createdAt: '2026-06-12T13:00:02.000Z',
        },
      ],
    };

    const data = buildCheckoutPaymentData(submit, preview, parentOrder);

    expect(data.parentOrderId).toBe(456);
    expect(data.orderCode).toBe('PO-456');
    expect(data.orders?.[0].orderCode).toBe('OR-99');
  });

  it('normalizes stale stored payment data without crashing on missing orders', () => {
    const staleData: CheckoutPaymentData = {
      sessionId: 'session-2',
      parentOrderId: 789,
      totalAmount: 120000,
      createdAt: '2026-06-12T13:00:00.000Z',
    };

    const data = normalizeCheckoutPaymentData(staleData);

    expect(data?.orders).toEqual([]);
    expect(data?.finalAmount).toBe(120000);
    expect(data?.orderCode).toBe('session-2');
  });

  it('calculates and formats payment deadline countdowns', () => {
    expect(getPaymentDeadlineAt('2026-06-12T13:00:00.000Z')).toBe('2026-06-12T13:05:00.000Z');
    expect(getPaymentRemainingSeconds(
      '2026-06-12T13:00:00.000Z',
      new Date('2026-06-12T13:02:30.000Z').getTime(),
    )).toBe(150);
    expect(formatPaymentCountdown(150)).toBe('2 phút 30 giây');
    expect(formatPaymentCountdown(65)).toBe('1 phút 05 giây');
    expect(formatPaymentCountdown(0)).toBe('0 giây');
  });
});
