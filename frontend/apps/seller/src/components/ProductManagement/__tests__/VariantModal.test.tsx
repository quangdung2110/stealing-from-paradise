import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import VariantModal from '../VariantModal';
import { sellerApi } from '@shared/api/seller.api';

vi.mock('@shared/api/seller.api', () => ({
  sellerApi: {
    createVariant: vi.fn(() => Promise.resolve({ data: { data: {} } })),
    updateVariant: vi.fn(() => Promise.resolve({ data: { data: {} } })),
  },
}));

function renderModal(initial?: any) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <VariantModal productId="p1" initial={initial} onClose={vi.fn()} onSuccess={vi.fn()} />
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.clearAllMocks());

describe('VariantModal (UC-PRODUCT-004)', () => {
  it('validates required fields before saving', async () => {
    renderModal();
    // Auto-generated SKU and name are filled, but price is empty → triggers "Giá phải lớn hơn 0"
    fireEvent.click(screen.getByRole('button', { name: /Tạo biến thể/i }));
    expect(await screen.findByText(/Giá phải lớn hơn 0/i)).toBeInTheDocument();
    expect(sellerApi.createVariant).not.toHaveBeenCalled();
  });

  it('creates a variant with the typed values', async () => {
    renderModal();
    fireEvent.change(screen.getByPlaceholderText(/IPHONE15-BLK-128/i), { target: { value: 'SKU1' } });
    fireEvent.change(screen.getByPlaceholderText(/iPhone 15 Black 128GB/i), { target: { value: 'V1' } });
    const numbers = screen.getAllByRole('spinbutton');
    fireEvent.change(numbers[0], { target: { value: '5000' } }); // price
    fireEvent.click(screen.getByRole('button', { name: /Tạo biến thể/i }));
    await waitFor(() => expect(sellerApi.createVariant).toHaveBeenCalledWith('p1', {
      skuCode: 'SKU1', variantName: 'V1', price: 5000, stock: 1,
    }));
  });
});
