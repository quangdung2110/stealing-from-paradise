import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import OrderDrawer from '../OrderDrawer';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

const order: any = {
  orderId: 7, orderCode: 'ORD-7', buyerId: 5, buyerName: 'Khách', buyerUsername: 'khach',
  status: 'SHIPPING', totalAmt: 2000, finalAmt: 1800, trackingNumber: 'VN9', carrier: 'GHN',
  shippingAddress: { fullAddress: '1 Đường ABC', provinceId: 1, districtId: 1 },
  itemCount: 1, createdAt: '2026-01-01T00:00:00Z',
};

beforeEach(() => vi.clearAllMocks());

describe('OrderDrawer', () => {
  it('renders buyer, shipping and payment info and status badge', () => {
    render(<MemoryRouter><OrderDrawer order={order} onClose={vi.fn()} /></MemoryRouter>);
    expect(screen.getByText('Khách')).toBeInTheDocument();
    expect(screen.getByText('VN9')).toBeInTheDocument();
    expect(screen.getByText('1 Đường ABC')).toBeInTheDocument();
    expect(screen.getByText('Đang giao')).toBeInTheDocument();
  });

  it('navigates to the detail page on "Xem chi tiết đầy đủ"', () => {
    const onClose = vi.fn();
    render(<MemoryRouter><OrderDrawer order={order} onClose={onClose} /></MemoryRouter>);
    fireEvent.click(screen.getByText(/Xem chi tiết đầy đủ/i));
    expect(onClose).toHaveBeenCalled();
    expect(mockNavigate).toHaveBeenCalledWith('/orders/7');
  });
});
