import Cookies from 'js-cookie';

export function getCookieName(baseName: string): string {
  if (typeof window === 'undefined') return baseName;
  
  const pathname = window.location.pathname;
  const port = window.location.port;

  // 1. Check pathname (for Nginx proxy case / production)
  if (pathname.startsWith('/seller')) {
    return `seller_${baseName}`;
  }
  if (pathname.startsWith('/admin')) {
    return `admin_${baseName}`;
  }

  // 2. Check port (for local development directly hitting Vite dev servers)
  if (port === '3001') {
    return `seller_${baseName}`;
  }
  if (port === '3002') {
    return `admin_${baseName}`;
  }
  if (port === '3000') {
    return `customer_${baseName}`;
  }

  // Default to customer prefix
  return `customer_${baseName}`;
}

export const authCookies = {
  get: (name: string): string | undefined => {
    return Cookies.get(getCookieName(name));
  },
  set: (name: string, value: string, options?: Cookies.CookieAttributes): void => {
    Cookies.set(getCookieName(name), value, options);
  },
  remove: (name: string, options?: Cookies.CookieAttributes): void => {
    Cookies.remove(getCookieName(name), options);
  }
};
