import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/utils';
import SellerPaymentsPage from '../SellerPaymentsPage';
import { sellerApi } from '@shared/api/seller.api';

vi.mock('@shared/api/seller.api', () => ({
  sellerApi: {
    getEarnings: vi.fn(),
    getStripeDashboardLink: vi.fn(() => Promise.resolve({ data: { data: { dashboardUrl: 'https://dash/x' } } })),
    getStripeStatus: vi.fn(() => Promise.resolve({ data: { data: null } })),
  },
}));

const transfer = (over: any) => ({
  id: 1, orderId: 100, transferAmount: 100000, feeAmount: 3000, netAmount: 97000,
  stripeTransferId: 'tr_1', status: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', ...over,
});

const setEarnings = (transfers: any[]) => (sellerApi.getEarnings as any).mockResolvedValue({
  data: { data: { totalEarnings: 500000, availableBalance: 300000, pendingBalance: 200000, platformFeePercentage: 3, totalOrders: transfers.length, transfers } },
});

beforeEach(() => vi.clearAllMocks());

describe('SellerPaymentsPage — earnings & transfer status', () => {
  it('renders transfer-status chips for each transfer state', async () => {
    setEarnings([
      transfer({ id: 1, status: 'SUCCEEDED' }),
      transfer({ id: 2, status: 'PENDING' }),
      transfer({ id: 3, status: 'FAILED' }),
      transfer({ id: 4, status: 'REVERSED' }),
    ]);
    renderWithProviders(<SellerPaymentsPage />, { route: '/payments' });
    // Status labels appear inside spans with icons - use getAllByText
    expect(await screen.findByText(/Đã chuyển/)).toBeInTheDocument();
    expect(screen.getByText(/Đang chờ/)).toBeInTheDocument();
    expect(screen.getByText(/Thất bại/)).toBeInTheDocument();
    expect(screen.getByText(/Bị đảo ngược/)).toBeInTheDocument();
  });

  it('shows the empty state when there are no transfers', async () => {
    setEarnings([]);
    renderWithProviders(<SellerPaymentsPage />, { route: '/payments' });
    expect(await screen.findByText(/Chưa có thu nhập nào/i)).toBeInTheDocument();
  });

  it('opens the Stripe Dashboard via getStripeDashboardLink', async () => {
    setEarnings([]);
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderWithProviders(<SellerPaymentsPage />, { route: '/payments' });
    // Switch to Stripe tab first
    fireEvent.click(await screen.findByRole('button', { name: /Stripe Dashboard/i }));
    fireEvent.click(screen.getByRole('button', { name: /Mở Stripe Dashboard/i }));
    await waitFor(() => expect(sellerApi.getStripeDashboardLink).toHaveBeenCalled());
    await waitFor(() => expect(openSpy).toHaveBeenCalledWith('https://dash/x', '_blank', 'noopener,noreferrer'));
    openSpy.mockRestore();
  });
});
