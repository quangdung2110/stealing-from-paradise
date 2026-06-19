import { describe, expect, it } from 'vitest';
import type { Notification } from '../api/notification.api';
import { getNotificationTarget } from '../utils/notificationRouting';

const notif = (overrides: Partial<Notification>): Notification => ({
  id: 'n1',
  userId: 1,
  type: 'INFO',
  title: 't',
  message: 'm',
  read: false,
  createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
});

describe('getNotificationTarget', () => {
  it('prefers explicit internal deeplinks', () => {
    expect(getNotificationTarget(notif({ data: { deeplink: '/orders/123' } }), 'BUYER')).toBe('/orders/123');
  });

  it('routes nested order payloads for buyers', () => {
    expect(getNotificationTarget(notif({
      type: 'ORDER_STATUS',
      data: { payload: { order_id: 42 } },
    }), 'BUYER')).toBe('/orders/42');
  });

  it('routes refunds to seller order detail when order id exists', () => {
    expect(getNotificationTarget(notif({
      type: 'REFUND_REQUESTED',
      data: { refund_id: 9, order_id: 42 },
    }), 'SELLER')).toBe('/orders/42');
  });

  it('routes admin stripe notifications to the seller stripe console', () => {
    expect(getNotificationTarget(notif({ type: 'STRIPE_ACCOUNT_UPDATED' }), 'ADMIN')).toBe('/sellers-stripe');
  });
});
