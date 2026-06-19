import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/utils';
import SellerOrderDetailPage from '../SellerOrderDetailPage';
import { orderApi } from '@shared/api/order.api';
import { paymentApi } from '@shared/api/payment.api';

vi.mock('@shared/api/order.api', () => ({ orderApi: { getOrderById: vi.fn() } }));
vi.mock('@shared/api/payment.api', () => ({ paymentApi: { getPayment: vi.fn(() => Promise.resolve({ data: { data: null } })) } }));

const order = (over: any) => ({
  orderId: 42, parentOrderId: 100, orderCode: 'ORD-42', buyerId: 5, buyerName: 'Khách',
  status: 'PAID', totalAmt: 1000, finalAmt: 1000, trackingNumber: null,
  items: [], createdAt: '2026-01-01T00:00:00Z', ...over,
});

const loadOrder = (o: any) => (orderApi.getOrderById as any).mockResolvedValue({ data: { data: o } });

beforeEach(() => vi.clearAllMocks());

describe('SellerOrderDetailPage — action strip per status', () => {
  it('PAID (not shipped) → ship + cancel actions', async () => {
    loadOrder(order({ status: 'PAID', trackingNumber: null }));
    renderWithProviders(<SellerOrderDetailPage />, { route: '/orders/42', path: '/orders/:orderId' });
    expect(await screen.findByText(/Cập nhật vận đơn/i)).toBeInTheDocument();
    expect(screen.getByText('Huỷ đơn')).toBeInTheDocument();
    expect(screen.queryByText(/Hoàn hàng/i)).not.toBeInTheDocument();
  });

  it('SHIPPING → return-to-sender action only', async () => {
    loadOrder(order({ status: 'SHIPPING', trackingNumber: 'VN1' }));
    renderWithProviders(<SellerOrderDetailPage />, { route: '/orders/42', path: '/orders/:orderId' });
    expect(await screen.findByText(/Hoàn hàng/i)).toBeInTheDocument();
    expect(screen.queryByText(/Cập nhật vận đơn/i)).not.toBeInTheDocument();
    expect(screen.queryByText('Huỷ đơn')).not.toBeInTheDocument();
  });

  it('not-found → error message', async () => {
    (orderApi.getOrderById as any).mockRejectedValue({ response: { status: 404 } });
    renderWithProviders(<SellerOrderDetailPage />, { route: '/orders/42', path: '/orders/:orderId' });
    // The page's useQuery sets retry:1, so allow time for the retry + error render.
    expect(await screen.findByText(/Không tìm thấy đơn hàng/i, {}, { timeout: 5000 })).toBeInTheDocument();
  });
});
