import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import RTSModal from '../RTSModal';
import { orderApi } from '@shared/api/order.api';

vi.mock('@shared/api/order.api', () => ({
  orderApi: {
    returnToSender: vi.fn(() => Promise.resolve({ data: { data: {} } })),
  },
}));

function renderModal() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const onSuccess = vi.fn();
  const onClose = vi.fn();
  render(
    <QueryClientProvider client={qc}>
      <RTSModal orderId={7} orderCode="ORD-7" onClose={onClose} onSuccess={onSuccess} />
    </QueryClientProvider>,
  );
  return { onSuccess, onClose };
}

const confirmBtn = () => screen.getByRole('button', { name: /Xác nhận hoàn hàng/i });
const trackingBox = () => screen.getByPlaceholderText(/Mã vận đơn từ đơn vị/i);
const fileInput = () => document.querySelector('input[type="file"]') as HTMLInputElement;

function makeFile(name: string) {
  return new File(['x'], name, { type: 'image/png' });
}

describe('RTSModal (UC-ORDER-006 Flow B)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('keeps confirm disabled until both tracking number and an image are provided', () => {
    renderModal();
    expect(confirmBtn()).toBeDisabled();

    // Only tracking, no image → still disabled.
    fireEvent.change(trackingBox(), { target: { value: 'RET-123' } });
    expect(confirmBtn()).toBeDisabled();
  });

  it('enables confirm and submits multipart data when tracking + image present', async () => {
    const { onSuccess } = renderModal();
    fireEvent.change(trackingBox(), { target: { value: 'RET-123' } });
    fireEvent.change(fileInput(), { target: { files: [makeFile('a.png')] } });

    expect(confirmBtn()).toBeEnabled();
    fireEvent.click(confirmBtn());

    await waitFor(() => expect(orderApi.returnToSender).toHaveBeenCalledTimes(1));
    const [calledOrderId, fd] = (orderApi.returnToSender as any).mock.calls[0];
    expect(calledOrderId).toBe(7);
    expect(fd).toBeInstanceOf(FormData);
    expect(fd.get('return_tracking_number')).toBe('RET-123');
    expect(fd.getAll('evidence_images')).toHaveLength(1);
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });
});
