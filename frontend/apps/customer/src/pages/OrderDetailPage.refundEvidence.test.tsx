import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { orderApi } from '@shared/api/order.api';
import { paymentApi } from '@shared/api/payment.api';
import { refundApi } from '@shared/api/refund.api';
import OrderDetailPage from './OrderDetailPage';

vi.mock('@shared/api/order.api', () => ({
  orderApi: {
    getParentOrder: vi.fn(),
    cancelOrder: vi.fn(),
    confirmReceived: vi.fn(),
  },
}));

vi.mock('@shared/api/payment.api', () => ({
  paymentApi: {
    getPayment: vi.fn(),
  },
}));

vi.mock('@shared/api/refund.api', () => ({
  refundApi: {
    getRefundPresignedUrl: vi.fn(),
    requestPartialRefund: vi.fn(),
    requestFullRefund: vi.fn(),
  },
}));

vi.mock('@shared/lib/toast', () => ({
  notify: {
    error: vi.fn(),
    success: vi.fn(),
  },
}));

const parentOrder = {
  parentOrderId: 1,
  orderCode: 'PO-1',
  status: 'DELIVERED',
  totalAmt: 100_000,
  finalAmt: 100_000,
  shippingAddress: { fullAddress: '123 Test St', provinceId: 1, districtId: 1 },
  createdAt: '2026-01-01T00:00:00.000Z',
  orders: [
    {
      orderId: 10,
      parentOrderId: 1,
      orderCode: 'ORD-10',
      sellerId: 1,
      sellerName: 'Test Shop',
      status: 'DELIVERED',
      totalAmt: 100_000,
      finalAmt: 100_000,
      itemCount: 1,
      createdAt: '2026-01-01T00:00:00.000Z',
      items: [
        {
          orderItemId: 100,
          skuCode: 'SKU-1',
          productName: 'Demo product',
          variantName: 'Default',
          priceSnapshot: 100_000,
          quantity: 1,
          refundedQuantity: 0,
        },
      ],
    },
  ],
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/orders/1']}>
        <Routes>
          <Route path="/orders/:parentOrderId" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('OrderDetailPage refund evidence upload', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }));
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:refund-preview'),
    });

    vi.mocked(orderApi.getParentOrder).mockResolvedValue({ data: { data: parentOrder } } as any);
    vi.mocked(paymentApi.getPayment).mockResolvedValue({
      data: {
        data: {
          amount: 100_000,
          method: 'stripe',
          status: 'SUCCESS',
          paidAt: '2026-01-01T00:05:00.000Z',
        },
      },
    } as any);
    vi.mocked(refundApi.getRefundPresignedUrl).mockResolvedValue({
      data: {
        data: {
          url: 'http://localhost:9000/refund-evidences/refunds/10/evidence.png?X-Amz-Signature=abc',
          contentType: 'image/png',
        },
      },
    } as any);
  });

  it('uses a local blob URL for image preview instead of the API upload route', async () => {
    const { container } = renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /Ho.n ti.n m.t ph.n/i }));

    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['png'], 'evidence.png', { type: 'image/png' });
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledTimes(1));
    expect(globalThis.fetch).toHaveBeenCalledWith(
      'http://localhost:9000/refund-evidences/refunds/10/evidence.png?X-Amz-Signature=abc',
      expect.objectContaining({ method: 'PUT' }),
    );

    const preview = await screen.findByAltText('evidence.png');
    expect(preview).toHaveAttribute('src', 'blob:refund-preview');

    const imageSources = Array.from(container.querySelectorAll('img')).map(img => img.getAttribute('src') ?? '');
    expect(imageSources.some(src => src.includes('/api/v1/orders'))).toBe(false);
  });
});
