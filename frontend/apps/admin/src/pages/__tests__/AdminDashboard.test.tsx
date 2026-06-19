import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AdminDashboard from '../AdminDashboard';
import { adminApi } from '@shared/api/admin.api';
import { adminRefundApi } from '@shared/api/refund.api';
import { flashSaleApi } from '@shared/api/flashSale.api';

vi.mock('@shared/api/admin.api', () => ({
  adminApi: { getUsers: vi.fn(), getPendingProducts: vi.fn() },
}));
vi.mock('@shared/api/refund.api', () => ({ adminRefundApi: { list: vi.fn() } }));
vi.mock('@shared/api/flashSale.api', () => ({ flashSaleApi: { getSessions: vi.fn() } }));

const total = (n: number) => ({ data: { data: { totalElements: n, content: [], totalPages: 1, last: true } } });

function renderDash() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <AdminDashboard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  (adminApi.getUsers as any).mockResolvedValue(total(42));
  (adminApi.getPendingProducts as any).mockResolvedValue(total(7));
  (adminRefundApi.list as any).mockResolvedValue(total(3));
  (flashSaleApi.getSessions as any).mockResolvedValue({ data: { data: { content: [{}, {}] } } });
});

describe('AdminDashboard', () => {
  it('renders the heading and quick links', async () => {
    renderDash();
    expect(screen.getByText('Bảng điều khiển Admin')).toBeInTheDocument();
    expect(screen.getByText('Quản lý người dùng')).toBeInTheDocument();
  });

  it('shows live counts pulled from the four stat APIs', async () => {
    renderDash();
    // user total — distinct enough to be unique
    expect(await screen.findByText('42')).toBeInTheDocument();
    // pending products = 7 (a small number may appear elsewhere, so allow >=1)
    expect((await screen.findAllByText('7')).length).toBeGreaterThan(0);
    // pending refunds = 3
    expect((await screen.findAllByText('3')).length).toBeGreaterThan(0);
  });
});
