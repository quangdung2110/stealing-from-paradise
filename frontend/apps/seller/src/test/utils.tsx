/**
 * Shared test helper: render a component inside the providers the seller app
 * relies on — a fresh React Query client (retries off so failures surface
 * immediately) and a MemoryRouter. Pass `path` to exercise a route param
 * (e.g. `/orders/:orderId`); otherwise the component renders at `route`.
 */
import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

export function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

interface Options {
  /** Initial URL (with query string / param value), default '/'. */
  route?: string;
  /** Route pattern to match `route` against (enables useParams). */
  path?: string;
}

export function renderWithProviders(ui: ReactElement, { route = '/', path }: Options = {}) {
  const queryClient = makeQueryClient();
  const tree = path ? (
    <Routes>
      <Route path={path} element={ui} />
    </Routes>
  ) : ui;

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>{tree}</MemoryRouter>
    </QueryClientProvider>,
  );
}
