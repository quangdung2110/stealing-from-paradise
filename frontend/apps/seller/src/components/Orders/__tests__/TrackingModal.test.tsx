import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TrackingModal from '../TrackingModal';
import { orderApi } from '@shared/api/order.api';

vi.mock('@shared/api/order.api', () => ({
  orderApi: { updateTracking: vi.fn(() => Promise.resolve({ data: { data: {} } })) },
}));

function renderModal() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const onSuccess = vi.fn();
  render(
    <QueryClientProvider client={qc}>
      <TrackingModal orderId={9} orderCode="ORD-9" customerLabel="Khách" onClose={vi.fn()} onSuccess={onSuccess} />
    </QueryClientProvider>,
  );
  return { onSuccess };
}

const confirmBtn = () => screen.getByRole('button', { name: /Xác nhận giao hàng/i });

describe('TrackingModal (UC-ORDER-004)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('disables confirm until a tracking number is entered', () => {
    renderModal();
    expect(confirmBtn()).toBeDisabled();
    fireEvent.change(screen.getByPlaceholderText(/VT123456789/i), { target: { value: 'VN123' } });
    expect(confirmBtn()).toBeEnabled();
  });

  it('submits trackingNumber + default carrier to updateTracking', async () => {
    const { onSuccess } = renderModal();
    fireEvent.change(screen.getByPlaceholderText(/VT123456789/i), { target: { value: 'VN123' } });
    fireEvent.click(confirmBtn());
    await waitFor(() => expect(orderApi.updateTracking).toHaveBeenCalledTimes(1));
    expect(orderApi.updateTracking).toHaveBeenCalledWith(9, expect.objectContaining({
      trackingNumber: 'VN123', carrier: 'ViettelPost',
    }));
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });

  it('displays the API error message on rejection', async () => {
    (orderApi.updateTracking as any).mockRejectedValueOnce({
      response: {
        data: {
          message: 'Mã vận đơn không hợp lệ từ đơn vị vận chuyển',
        },
      },
    });

    renderModal();
    fireEvent.change(screen.getByPlaceholderText(/VT123456789/i), { target: { value: 'VN123' } });
    fireEvent.click(confirmBtn());

    expect(await screen.findByText('Mã vận đơn không hợp lệ từ đơn vị vận chuyển')).toBeInTheDocument();
  });
});
