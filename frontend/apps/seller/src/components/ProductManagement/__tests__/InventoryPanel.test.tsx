import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import InventoryPanel from '../InventoryPanel';
import { sellerApi } from '@shared/api/seller.api';

vi.mock('@shared/api/seller.api', () => ({
  sellerApi: {
    adjustInventory: vi.fn(() => Promise.resolve({ data: { data: {} } })),
    restockInventory: vi.fn(() => Promise.resolve({ data: { data: {} } })),
    getInventoryLogs: vi.fn(() => Promise.resolve({ data: { data: [] } })),
  },
}));

const variant = { id: 'v1', skuCode: 'SKU1', variantName: 'Đỏ', price: 1000, stock: 10 };

function renderPanel(variants: any[] = [variant]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <InventoryPanel productId="p1" variants={variants} />
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.clearAllMocks());

describe('InventoryPanel (UC-PRODUCT-006)', () => {
  it('shows a hint when there are no variants', () => {
    renderPanel([]);
    expect(screen.getByText(/Chưa có biến thể nào/i)).toBeInTheDocument();
  });

  it('adjusts stock by +1 via the quick button + confirm', async () => {
    renderPanel();
    fireEvent.click(screen.getByText('+1'));
    // Updated button text from "Xác nhận" to "Điều chỉnh"
    fireEvent.click(screen.getByRole('button', { name: /Điều chỉnh/i }));
    await waitFor(() => expect(sellerApi.adjustInventory).toHaveBeenCalledWith({
      skuCode: 'SKU1', delta: 1, reason: 'Điều chỉnh',
    }));
  });
});
