import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CancelOrderModal from '../CancelOrderModal';
import { orderApi } from '@shared/api/order.api';

// Mock the order API so no real HTTP call is made.
vi.mock('@shared/api/order.api', () => ({
  orderApi: {
    cancelOrder: vi.fn(() => Promise.resolve({ data: { data: {} } })),
  },
}));

function renderModal(props?: Partial<React.ComponentProps<typeof CancelOrderModal>>) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const onSuccess = vi.fn();
  const onClose = vi.fn();
  render(
    <QueryClientProvider client={qc}>
      <CancelOrderModal orderId={42} orderCode="ORD-42" onClose={onClose} onSuccess={onSuccess} {...props} />
    </QueryClientProvider>,
  );
  return { onSuccess, onClose };
}

const confirmBtn = () => screen.getByRole('button', { name: /Xác nhận huỷ đơn/i });
const reasonBox = () => screen.getByPlaceholderText(/Hết hàng/i);

describe('CancelOrderModal (UC-ORDER-008 / BR-ORDER-026)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('disables confirm until the reason reaches the 10-char minimum', () => {
    renderModal();
    expect(confirmBtn()).toBeDisabled();

    fireEvent.change(reasonBox(), { target: { value: 'short' } });
    expect(confirmBtn()).toBeDisabled();
    expect(screen.getAllByText(/tối thiểu 10 ký tự/i).length).toBeGreaterThan(0);
  });

  it('enables confirm once the reason is long enough', () => {
    renderModal();
    fireEvent.change(reasonBox(), { target: { value: 'Hết hàng không thể giao' } });
    expect(confirmBtn()).toBeEnabled();
  });

  it('submits the trimmed reason via the order API', async () => {
    const { onSuccess } = renderModal();
    fireEvent.change(reasonBox(), { target: { value: '  Hết hàng không thể giao  ' } });
    fireEvent.click(confirmBtn());

    await waitFor(() => expect(orderApi.cancelOrder).toHaveBeenCalledTimes(1));
    expect(orderApi.cancelOrder).toHaveBeenCalledWith(42, expect.objectContaining({
      reason: 'Hết hàng không thể giao',
    }));
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });

  it('displays the API error message on rejection', async () => {
    (orderApi.cancelOrder as any).mockRejectedValueOnce({
      response: {
        data: {
          message: 'Không thể hủy đơn hàng đã giao',
        },
      },
    });

    renderModal();
    fireEvent.change(reasonBox(), { target: { value: 'Lý do hủy đơn hàng trên 10 ký tự' } });
    fireEvent.click(confirmBtn());

    expect(await screen.findByText('Không thể hủy đơn hàng đã giao')).toBeInTheDocument();
  });
});
