import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, act } from '@testing-library/react';
import { renderWithProviders } from '@/test/utils';
import ProductManagementPage from '../ProductManagementPage';
import apiClient from '@shared/lib/axios';

vi.mock('@shared/lib/axios', () => {
  const m = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() };
  return { default: m, apiClient: m };
});

const product = (over: any) => ({
  productId: 'p1', name: 'SP', category: 'Cat', price: 1000, status: 'DRAFT',
  stockAvailable: 5, variantsCount: 0, createdAt: '2026-01-01T00:00:00Z', ...over,
});

/** Helper: set up apiClient.get to return a paginated product list. */
const list = (content: any[]) =>
  (apiClient.get as any).mockResolvedValue({ data: { data: { content, totalPages: 1, totalElements: content.length } } });

beforeEach(() => {
  vi.clearAllMocks();
  (apiClient.get as any).mockResolvedValue({ data: { data: { content: [], totalPages: 0, totalElements: 0 } } });
});

describe('ProductManagementPage — action matrix per status', () => {
  it('shows the right lifecycle action per product status', async () => {
    list([
      product({ productId: 'd', name: 'Draft SP', status: 'DRAFT' }),
      product({ productId: 'a', name: 'Approved SP', status: 'APPROVED' }),
      product({ productId: 'pub', name: 'Published SP', status: 'PUBLISHED' }),
      product({ productId: 'r', name: 'Rejected SP', status: 'REJECTED' }),
      product({ productId: 'pen', name: 'Pending SP', status: 'PENDING' }),
    ]);
    renderWithProviders(<ProductManagementPage />, { route: '/products' });
    expect(await screen.findByText('Draft SP')).toBeInTheDocument();

    // Button text now has emoji + label, use regex to match text content
    expect(screen.getAllByText(/Gửi duyệt/)).toHaveLength(1);   // DRAFT
    expect(screen.getAllByText(/Hiển thị/)).toHaveLength(1);    // APPROVED
    expect(screen.getAllByText(/Ẩn$/)).toHaveLength(1);          // PUBLISHED — exact "Ẩn"
    expect(screen.getAllByText(/Xóa/)).toHaveLength(2);          // DRAFT + REJECTED
  });

  it('shows the empty state when the seller has no products', async () => {
    list([]);
    renderWithProviders(<ProductManagementPage />, { route: '/products' });
    expect(await screen.findByText(/Chưa có sản phẩm nào/i)).toBeInTheDocument();
  });

  it('debounces the search input query by 300ms', async () => {
    vi.useFakeTimers();
    try {
      list([]);
      renderWithProviders(<ProductManagementPage />, { route: '/products' });

      // Flush initial queries (product list + count queries from ProductTabs)
      await act(async () => {
        await vi.runOnlyPendingTimersAsync();
      });

      const searchInput = screen.getByPlaceholderText(/Tìm sản phẩm/i);
      fireEvent.change(searchInput, { target: { value: 'laptop' } });

      // Count calls before debounce fires
      const beforeCalls = (apiClient.get as any).mock.calls.length;

      // Advance 299ms — debounce should not have fired yet
      await act(async () => {
        await vi.advanceTimersByTimeAsync(299);
      });
      expect(apiClient.get).toHaveBeenCalledTimes(beforeCalls);

      // Advance remaining 1ms — should trigger the debounced call
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1);
      });

      // Verify the debounced product search was called
      const calls = (apiClient.get as any).mock.calls;
      const productSearchCall = calls.find(
        (call: any) => call[0] === '/sellers/me/products' && call[1]?.params?.search === 'laptop',
      );
      expect(productSearchCall).toBeDefined();
    } finally {
      vi.useRealTimers();
    }
  });

  it('filters products by clicking status tabs and resets page', async () => {
    list([]);
    renderWithProviders(<ProductManagementPage />, { route: '/products' });

    // Wait for the initial product list call
    await waitFor(() => {
      const calls = (apiClient.get as any).mock.calls;
      const productCall = calls.find((c: any) => c[0] === '/sellers/me/products' && c[1]?.params?.status === undefined);
      expect(productCall).toBeDefined();
    });

    const publishedTab = screen.getByText('Đang bán');
    fireEvent.click(publishedTab);

    await waitFor(() => {
      const calls = (apiClient.get as any).mock.calls;
      const activeCall = calls.find((c: any) => c[0] === '/sellers/me/products' && c[1]?.params?.status === 'ACTIVE');
      expect(activeCall).toBeDefined();
    });
  });
});
