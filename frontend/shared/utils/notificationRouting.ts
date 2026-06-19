import type { Notification } from '../api/notification.api';

type NotificationRecord = Record<string, any>;

function isRecord(value: unknown): value is NotificationRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseRecord(value: unknown): NotificationRecord | undefined {
  if (isRecord(value)) return value;
  if (typeof value !== 'string' || value.trim() === '') return undefined;

  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

function collectDataRecords(notification: Notification): NotificationRecord[] {
  const records: NotificationRecord[] = [];
  const root = notification.data;
  if (!isRecord(root)) return records;

  records.push(root);
  for (const key of ['payload', 'data', 'metadata', 'event']) {
    const nested = parseRecord(root[key]);
    if (nested) records.push(nested);
  }

  return records;
}

function firstValue(records: NotificationRecord[], keys: string[]): unknown {
  for (const record of records) {
    for (const key of keys) {
      if (record[key] !== undefined && record[key] !== null && record[key] !== '') {
        return record[key];
      }
    }
  }
  return undefined;
}

function asPathSegment(value: unknown): string | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  if (typeof value === 'string' && value.trim()) return value.trim();
  return undefined;
}

function internalPath(value: unknown): string | undefined {
  const path = typeof value === 'string' ? value.trim() : '';
  return path.startsWith('/') && !path.startsWith('//') ? path : undefined;
}

function roleName(role?: string): string {
  return (role ?? '').toUpperCase();
}

export function getNotificationTarget(notification: Notification, role?: string): string | undefined {
  const records = collectDataRecords(notification);
  const directPath = internalPath(firstValue(records, ['deeplink', 'deepLink', 'targetUrl', 'target_url', 'url', 'path']));
  if (directPath) return directPath;

  const type = notification.type.toUpperCase();
  const userRole = roleName(role);
  const orderId = asPathSegment(firstValue(records, ['parentOrderId', 'parent_order_id', 'orderId', 'order_id']));
  const refundId = asPathSegment(firstValue(records, ['refundId', 'refund_id']));
  const productId = asPathSegment(firstValue(records, ['productId', 'product_id', 'productSlug', 'product_slug']));

  if (type.includes('REFUND') || refundId) {
    if (userRole === 'SELLER') return orderId ? `/orders/${orderId}` : '/orders';
    return '/refunds';
  }

  if (type.includes('ORDER') || type.includes('PAYMENT') || orderId) {
    if (userRole === 'ADMIN') return undefined;
    return orderId ? `/orders/${orderId}` : '/orders';
  }

  if (type.includes('FLASH') || type.includes('PROMO') || firstValue(records, ['sessionId', 'session_id', 'fsSessionId', 'fs_session_id'])) {
    return userRole === 'ADMIN' ? '/flash-sale-config' : '/flash-sales';
  }

  if (type.includes('PRODUCT') || productId) {
    if (userRole === 'ADMIN') return '/product-moderation';
    return productId ? `/products/${productId}` : '/products';
  }

  if (type.includes('TRANSFER') || type.includes('PAYOUT')) {
    return userRole === 'SELLER' ? '/payments' : undefined;
  }

  if (type.includes('STRIPE')) {
    if (userRole === 'ADMIN') return '/sellers-stripe';
    if (userRole === 'SELLER') return '/stripe-onboarding';
  }

  if (type.includes('ACCOUNT') || type.includes('USER')) {
    return userRole === 'ADMIN' ? '/users' : '/settings';
  }

  return undefined;
}
