import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore, isAuthFromCookie } from '../store/authStore';
import { useCartStore } from '../store/cartStore';
import NotificationBell from './NotificationBell';
import { Icon } from './icons';
import type { NavItem, NavGroup } from './navConfig';

export type { NavItem, NavGroup } from './navConfig';
/** @deprecated kept so existing imports of `NavLink` keep compiling. */
export type NavLink = NavItem;

interface NavbarProps {
  appName: string;
  /** Top-priority destinations shown inline on the desktop bar. */
  primaryItems?: NavItem[];
  /** Secondary destinations, grouped inside the account dropdown. */
  menuGroups?: NavGroup[];
  /** Show the product search field in the header (customer app). */
  showSearch?: boolean;
  /** Show the cart icon with a live item-count badge (customer app). */
  showCart?: boolean;
}

export default function Navbar({
  appName,
  primaryItems = [],
  menuGroups = [],
  showSearch = false,
  showCart = false,
}: NavbarProps) {
  const { user, logout } = useAuthStore();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [search, setSearch] = useState(searchParams.get('q') ?? '');
  const menuRef = useRef<HTMLDivElement>(null);

  const cartCount = useCartStore((s) => s.cart?.totalItems ?? 0);
  const fetchCart = useCartStore((s) => s.fetchCart);

  const authed = isAuthFromCookie();
  const isActive = (to: string) => location.pathname === to;

  // Keep the header search box in sync with the URL's ?q= as the user navigates.
  useEffect(() => { setSearch(searchParams.get('q') ?? ''); }, [searchParams]);

  // Seed the cart badge once after login so the count is correct before the
  // user ever opens the cart page.
  useEffect(() => {
    if (showCart && authed) fetchCart();
  }, [showCart, authed, fetchCart]);

  // Close the account dropdown on any outside click.
  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setUserMenuOpen(false);
    }
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const handleLogout = async () => {
    setUserMenuOpen(false);
    await logout();
    navigate('/login');
  };

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const q = search.trim();
    navigate(q ? `/products?q=${encodeURIComponent(q)}` : '/products');
  };

  return (
    <nav className="bg-white/95 backdrop-blur border-b border-gray-200 sticky top-0 z-40 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-4 h-16">

          {/* Brand */}
          <Link to="/" className="flex items-center gap-2 shrink-0">
            <span className="flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-to-br from-blue-500 to-violet-600 text-white text-lg font-bold shadow-sm">
              ⚡
            </span>
            <span className="hidden sm:block font-bold text-gray-900 text-lg tracking-tight">{appName}</span>
          </Link>

          {/* Primary links — desktop only */}
          <div className="hidden md:flex items-center gap-1 shrink-0">
            {primaryItems.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive(item.to)
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                }`}
              >
                {item.iconKey && <Icon name={item.iconKey} className="w-4 h-4" />}
                {item.label}
              </Link>
            ))}
          </div>

          {/* Search — flexible center */}
          {showSearch && (
            <form onSubmit={submitSearch} className="flex-1 max-w-md hidden sm:block">
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none">
                  <Icon name="search" className="w-4 h-4" />
                </span>
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Tìm kiếm sản phẩm..."
                  className="w-full pl-9 pr-3 py-2 rounded-xl bg-gray-100 border border-transparent text-sm text-gray-900 placeholder-gray-400 focus:bg-white focus:border-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-500/20 transition-colors"
                />
              </div>
            </form>
          )}

          {/* Right cluster */}
          <div className="flex items-center gap-1.5 sm:gap-2 ml-auto shrink-0">
            {authed && user ? (
              <>
                {showCart && (
                  <Link
                    to="/cart"
                    className="relative p-2 rounded-xl text-gray-500 hover:text-gray-900 hover:bg-gray-100 transition-colors"
                    aria-label="Giỏ hàng"
                  >
                    <Icon name="cart" className="w-6 h-6" />
                    {cartCount > 0 && (
                      <span className="absolute top-1 right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-blue-600 px-1 text-[10px] font-bold text-white ring-2 ring-white">
                        {cartCount > 99 ? '99+' : cartCount}
                      </span>
                    )}
                  </Link>
                )}

                <NotificationBell />

                {/* Account dropdown — secondary links live here */}
                <div className="relative" ref={menuRef}>
                  <button
                    onClick={() => setUserMenuOpen((v) => !v)}
                    className="flex items-center gap-2 pl-2 pr-1.5 sm:pl-3 sm:pr-2 py-1.5 rounded-xl border border-gray-200 hover:border-gray-300 hover:bg-gray-50 transition-colors"
                  >
                    <span className="w-7 h-7 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-xs font-bold uppercase shrink-0">
                      {user.username.charAt(0)}
                    </span>
                    <span className="hidden lg:block text-sm font-medium text-gray-900 max-w-[8rem] truncate">
                      {user.username}
                    </span>
                    <Icon name="chevronDown" className={`w-4 h-4 text-gray-400 transition-transform ${userMenuOpen ? 'rotate-180' : ''}`} />
                  </button>

                  {userMenuOpen && (
                    <div className="absolute right-0 mt-2 w-60 bg-white rounded-2xl border border-gray-200 shadow-xl z-20 overflow-hidden">
                      <div className="px-4 py-3 border-b border-gray-100">
                        <p className="text-sm font-semibold text-gray-900 truncate">{user.username}</p>
                        <p className="text-xs text-gray-500 truncate">{user.email}</p>
                      </div>

                      <div className="py-1.5 max-h-[60vh] overflow-y-auto">
                        {menuGroups.map((group, gi) => (
                          <div key={gi} className={gi > 0 ? 'mt-1 pt-1 border-t border-gray-100' : ''}>
                            {group.label && (
                              <p className="px-4 pt-1.5 pb-1 text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                                {group.label}
                              </p>
                            )}
                            {group.items.map((item) => (
                              <Link
                                key={item.to}
                                to={item.to}
                                onClick={() => setUserMenuOpen(false)}
                                className={`flex items-center gap-3 px-4 py-2 text-sm transition-colors ${
                                  isActive(item.to) ? 'text-blue-700 bg-blue-50' : 'text-gray-700 hover:bg-gray-50'
                                }`}
                              >
                                {item.iconKey && <Icon name={item.iconKey} className="w-5 h-5 text-gray-400" />}
                                {item.label}
                              </Link>
                            ))}
                          </div>
                        ))}
                      </div>

                      <button
                        onClick={handleLogout}
                        className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 border-t border-gray-100 transition-colors"
                      >
                        <Icon name="logout" className="w-5 h-5" />
                        Đăng xuất
                      </button>
                    </div>
                  )}
                </div>
              </>
            ) : (
              <div className="flex items-center gap-2">
                <Link
                  to="/login"
                  className="px-4 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  Đăng nhập
                </Link>
                <Link
                  to="/register"
                  className="px-4 py-2 text-sm font-medium text-white bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 rounded-lg shadow-sm transition-colors"
                >
                  Đăng ký
                </Link>
              </div>
            )}
          </div>
        </div>

        {/* Mobile search row */}
        {showSearch && (
          <form onSubmit={submitSearch} className="sm:hidden pb-3">
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none">
                <Icon name="search" className="w-4 h-4" />
              </span>
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Tìm kiếm sản phẩm..."
                className="w-full pl-9 pr-3 py-2 rounded-xl bg-gray-100 border border-transparent text-sm text-gray-900 placeholder-gray-400 focus:bg-white focus:border-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-500/20 transition-colors"
              />
            </div>
          </form>
        )}
      </div>
    </nav>
  );
}
