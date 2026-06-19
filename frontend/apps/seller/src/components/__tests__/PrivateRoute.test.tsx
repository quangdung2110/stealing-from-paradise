import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import PrivateRoute from '@shared/components/PrivateRoute';

// Hoisted mutable auth state so each test can set authentication/role.
const h = vi.hoisted(() => ({ auth: { isAuthenticated: false, user: null as any }, cookie: false }));
vi.mock('@shared/store/authStore', () => ({
  useAuthStore: () => h.auth,
  isAuthFromCookie: () => h.cookie,
}));

function renderGuard() {
  render(
    <MemoryRouter initialEntries={['/secret']}>
      <Routes>
        <Route path="/login" element={<div>LOGIN</div>} />
        <Route path="/" element={<div>HOME</div>} />
        <Route path="/secret" element={<PrivateRoute role="SELLER"><div>SECRET</div></PrivateRoute>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  h.auth = { isAuthenticated: false, user: null };
  h.cookie = false;
});

describe('PrivateRoute (role=SELLER guard)', () => {
  it('redirects unauthenticated users to /login', () => {
    renderGuard();
    expect(screen.getByText('LOGIN')).toBeInTheDocument();
  });

  it('redirects an authenticated non-seller to /', () => {
    h.auth = { isAuthenticated: true, user: { role: 'BUYER' } };
    renderGuard();
    expect(screen.getByText('HOME')).toBeInTheDocument();
  });

  it('renders children for an authenticated SELLER', () => {
    h.auth = { isAuthenticated: true, user: { role: 'SELLER' } };
    renderGuard();
    expect(screen.getByText('SECRET')).toBeInTheDocument();
  });
});
