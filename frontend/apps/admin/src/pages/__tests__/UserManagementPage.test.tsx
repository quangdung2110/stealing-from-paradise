import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UserManagementPage from '../UserManagementPage';
import { adminApi } from '@shared/api/admin.api';

// Stub the admin API; every test controls what getUsers returns and asserts
// on how the page calls it.
vi.mock('@shared/api/admin.api', () => ({
  adminApi: {
    getUsers: vi.fn(),
  },
}));

const PAGE = (users: any[], over: any = {}) => ({
  data: { data: { content: users, totalElements: users.length, totalPages: 1, last: true, ...over } },
});

const USER = (over: any = {}) => ({
  userId: 1, username: 'minhhoa', email: 'minhhoa@example.com',
  role: 'BUYER', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', ...over,
});

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <UserManagementPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  (adminApi.getUsers as any).mockResolvedValue(PAGE([USER()]));
});

describe('UserManagementPage', () => {
  it('renders the user list returned by the API', async () => {
    renderPage();
    expect(await screen.findByText('minhhoa@example.com')).toBeInTheDocument();
    expect(screen.getByText('minhhoa')).toBeInTheDocument();
  });

  it('shows the empty state when no users come back', async () => {
    (adminApi.getUsers as any).mockResolvedValue(PAGE([]));
    renderPage();
    expect(await screen.findByText('Không có người dùng nào')).toBeInTheDocument();
  });

  // ── Regression: the search box used to be inert (searchQuery never reached
  // the API). These two lock in that it is now wired through.
  it('passes the typed search term to getUsers (debounced)', async () => {
    renderPage();
    await waitFor(() => expect(adminApi.getUsers).toHaveBeenCalled());

    const box = screen.getByPlaceholderText('Tìm theo tên, email...');
    fireEvent.change(box, { target: { value: 'hoa' } });

    // real 400ms debounce — give waitFor enough room to observe the call
    await waitFor(
      () =>
        expect(adminApi.getUsers).toHaveBeenCalledWith(
          expect.objectContaining({ search: 'hoa' }),
        ),
      { timeout: 2000 },
    );
  });

  it('does not send a search param while the box is empty', async () => {
    renderPage();
    await waitFor(() => expect(adminApi.getUsers).toHaveBeenCalled());
    // every call so far must have search === undefined
    for (const call of (adminApi.getUsers as any).mock.calls) {
      expect(call[0].search).toBeUndefined();
    }
  });

  it('requests the chosen role filter and resets to page 0', async () => {
    renderPage();
    await screen.findByText('minhhoa@example.com');
    fireEvent.click(screen.getByRole('button', { name: 'SELLER' }));
    await waitFor(() =>
      expect(adminApi.getUsers).toHaveBeenCalledWith(
        expect.objectContaining({ role: 'SELLER', page: 0 }),
      ),
    );
  });
});
