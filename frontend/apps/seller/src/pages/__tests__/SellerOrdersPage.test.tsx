import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/utils';
import SellerOrdersPage from '../SellerOrdersPage';
import { orderApi } from '@shared/api/order.api';

vi.mock('@shared/api/order.api', () => ({
  orderApi: { getSellerOrders: vi.fn() },
}));

const order = (over: any) => ({
  orderId: 1, parentOrderId: 1, orderCode: 'O1', buyerId: 5, buyerName: 'Khách',
  status: 'PAID', totalAmt: 1000, finalAmt: 1000, itemCount: 1, trackingNumber: null,
  createdAt: '2026-01-01T00:00:00Z', ...over,
});

function load(orders: any[]) {
  (orderApi.getSellerOrders as any).mockResolvedValue({ data: { data: { content: orders, totalPages: 1 } } });
}

beforeEach(() => vi.clearAllMocks());

// Use exact action names from the component (they have emoji/icons)
const findShipAction = () => screen.queryByText(/Vận đơn/);
const findCancelAction = () => screen.queryByText(/^✕\s*Huỷ$/);
const findRtsAction = () => screen.queryByText(/^↩\s*Hoàn$/);
// The filter tab "Hoàn một phần" exists in the UI too

describe('SellerOrdersPage — action matrix per status', () => {
  it('PAID (not shipped) → ship + cancel, no RTS', async () => {
    load([order({ orderId: 1, orderCode: 'PAID-1', status: 'PAID', trackingNumber: null })]);
    renderWithProviders(<SellerOrdersPage />, { route: '/orders' });
    expect(await screen.findByText('PAID-1')).toBeInTheDocument();
    // Use getByText with exact match for action buttons (they have specific text)
    expect(screen.getByText('📦 Vận đơn')).toBeInTheDocument();
    expect(screen.getByText('✕ Huỷ')).toBeInTheDocument();
    expect(screen.queryByText('↩ Hoàn')).not.toBeInTheDocument();
  });

  it('SHIPPING → only return-to-sender', async () => {
    load([order({ orderId: 2, orderCode: 'SHIP-1', status: 'SHIPPING', trackingNumber: 'VN1' })]);
    renderWithProviders(<SellerOrdersPage />, { route: '/orders' });
    expect(await screen.findByText('SHIP-1')).toBeInTheDocument();
    expect(screen.getByText('↩ Hoàn')).toBeInTheDocument();
    expect(screen.queryByText('📦 Vận đơn')).not.toBeInTheDocument();
    expect(screen.queryByText('✕ Huỷ')).not.toBeInTheDocument();
  });

  it('PENDING and DELIVERED → no seller actions', async () => {
    load([
      order({ orderId: 3, orderCode: 'PEND-1', status: 'PENDING' }),
      order({ orderId: 4, orderCode: 'DELI-1', status: 'DELIVERED', trackingNumber: 'VN9' }),
    ]);
    renderWithProviders(<SellerOrdersPage />, { route: '/orders' });
    expect(await screen.findByText('PEND-1')).toBeInTheDocument();
    expect(screen.queryByText('📦 Vận đơn')).not.toBeInTheDocument();
    expect(screen.queryByText('✕ Huỷ')).not.toBeInTheDocument();
    expect(screen.queryByText('↩ Hoàn')).not.toBeInTheDocument();
  });

  it('empty result → empty-state message', async () => {
    load([]);
    renderWithProviders(<SellerOrdersPage />, { route: '/orders' });
    expect(await screen.findByText(/Chưa có đơn hàng nào/i)).toBeInTheDocument();
  });
});
