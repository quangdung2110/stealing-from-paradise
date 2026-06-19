import { Link, useLocation } from 'react-router-dom';
import { useCartStore } from '../store/cartStore';
import { isAuthFromCookie } from '../store/authStore';
import { Icon } from './icons';
import type { NavItem } from './navConfig';

interface BottomTabBarProps {
  items: NavItem[];
}

/**
 * Fixed bottom navigation for the customer app on mobile. Replaces the old
 * hamburger sheet so the primary destinations are always one tap away.
 */
export default function BottomTabBar({ items }: BottomTabBarProps) {
  const location = useLocation();
  const cartCount = useCartStore((s) => s.cart?.totalItems ?? 0);
  const authed = isAuthFromCookie();
  const isActive = (to: string) =>
    location.pathname === to || (to !== '/' && location.pathname.startsWith(to + '/'));

  return (
    <nav className="md:hidden fixed bottom-0 inset-x-0 z-40 bg-white/95 backdrop-blur border-t border-gray-200 pb-[env(safe-area-inset-bottom)]">
      <div className="grid grid-cols-4">
        {items.slice(0, 4).map((item) => {
          const active = isActive(item.to);
          const showBadge = item.badge === 'cart' && authed && cartCount > 0;
          return (
            <Link
              key={item.to}
              to={item.to}
              className={`relative flex flex-col items-center justify-center gap-0.5 py-2 text-[11px] font-medium transition-colors ${
                active ? 'text-blue-600' : 'text-gray-500 hover:text-gray-800'
              }`}
            >
              <span className="relative">
                {item.iconKey && <Icon name={item.iconKey} className="w-6 h-6" strokeWidth={active ? 2 : 1.8} />}
                {showBadge && (
                  <span className="absolute -top-1 -right-2 flex h-4 min-w-4 items-center justify-center rounded-full bg-blue-600 px-1 text-[9px] font-bold text-white ring-2 ring-white">
                    {cartCount > 99 ? '99+' : cartCount}
                  </span>
                )}
              </span>
              {item.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
