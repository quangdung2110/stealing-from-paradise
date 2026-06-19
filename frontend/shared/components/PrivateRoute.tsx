import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore, isAuthFromCookie } from '../store/authStore';

interface PrivateRouteProps {
  children: React.ReactNode;
  /** Required role, e.g. "SELLER", "ADMIN". If omitted, only checks authentication. */
  role?: string;
  /** Where to redirect unauthenticated users (default: /login) */
  loginPath?: string;
}

export default function PrivateRoute({ children, role, loginPath = '/login' }: PrivateRouteProps) {
  const { isAuthenticated, user } = useAuthStore();

  const authed = isAuthenticated || isAuthFromCookie();

  if (!authed) return <Navigate to={loginPath} replace />;
  if (role && user?.role !== role) return <Navigate to="/" replace />;

  return children;
}
