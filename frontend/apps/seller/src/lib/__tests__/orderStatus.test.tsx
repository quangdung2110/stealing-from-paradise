import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ORDER_STATUS_META, getStatusMeta, OrderStatusBadge } from '../orderStatus';
import type { OrderStatus } from '@shared/api/order.api';

const ALL_STATUSES: OrderStatus[] = [
  'PENDING', 'PAID', 'SHIPPING', 'DELIVERED',
  'CANCELLED', 'RETURNED', 'PARTIALLY_REFUNDED', 'REFUNDED',
];

describe('ORDER_STATUS_META', () => {
  it('has a label and chip styles for every order status', () => {
    ALL_STATUSES.forEach(s => {
      const meta = ORDER_STATUS_META[s];
      expect(meta.label.length).toBeGreaterThan(0);
      expect(meta.bg).toMatch(/^bg-/);
      expect(meta.color).toMatch(/^text-/);
    });
  });
});

describe('getStatusMeta', () => {
  it('resolves a known status', () => {
    expect(getStatusMeta('PAID').label).toBe('Đã thanh toán');
  });

  it('falls back to the raw value for an unknown status', () => {
    expect(getStatusMeta('WEIRD').label).toBe('WEIRD');
  });
});

describe('OrderStatusBadge', () => {
  it('renders the localized label', () => {
    render(<OrderStatusBadge status="SHIPPING" />);
    expect(screen.getByText('Đang giao')).toBeInTheDocument();
  });
});
