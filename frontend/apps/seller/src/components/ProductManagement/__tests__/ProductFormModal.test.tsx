import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ProductFormModal from '../ProductFormModal';
import { sellerApi } from '@shared/api/seller.api';
import { categoryApi } from '@shared/api/category.api';

vi.mock('@shared/api/seller.api', () => ({
  sellerApi: {
    createProduct: vi.fn(() => Promise.resolve({ data: { data: { productId: 'new1' } } })),
    createVariant: vi.fn(() => Promise.resolve({ data: { data: { id: 'v1' } } })),
    updateProduct: vi.fn(() => Promise.resolve({ data: { data: { productId: 'p1' } } })),
    getVariants: vi.fn(() => Promise.resolve({ data: { data: [] } })),
  },
}));
vi.mock('@shared/api/category.api', () => ({
  categoryApi: {
    getCategories: vi.fn(() => Promise.resolve({
      data: { data: [{ categoryId: 'c1', name: 'Category 1' }] },
    })),
  },
}));

function renderModal(product?: any) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <ProductFormModal product={product} onClose={vi.fn()} onSuccess={vi.fn()} />
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ProductFormModal (UC-PRODUCT-003)', () => {
  it('validates name + price before creating', () => {
    renderModal();
    fireEvent.click(screen.getByRole('button', { name: /Tạo sản phẩm/i }));
    expect(screen.getByText(/Vui lòng điền tên và giá/i)).toBeInTheDocument();
    expect(sellerApi.createProduct).not.toHaveBeenCalled();
  });

  it('creates a product on valid input', async () => {
    renderModal();
    await screen.findByText('Category 1');
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'c1' } });
    fireEvent.change(screen.getByPlaceholderText(/Sony WH-1000XM5/i), { target: { value: 'New SP' } });
    fireEvent.change(screen.getAllByRole('spinbutton')[0], { target: { value: '1000' } }); // price
    fireEvent.click(screen.getByRole('button', { name: /Tạo sản phẩm/i }));
    await waitFor(() => expect(sellerApi.createProduct).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'New SP', categoryId: 'c1' }),
    ));
    await waitFor(() => expect(sellerApi.createVariant).toHaveBeenCalledWith(
      'new1',
      expect.objectContaining({ variantName: 'Default', price: 1000, stock: 1 }),
    ));
  });
});
