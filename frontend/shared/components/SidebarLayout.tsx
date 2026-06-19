import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import NotificationBell from './NotificationBell';
import { Icon } from './icons';
import type { NavGroup } from './navConfig';

interface SidebarLayoutProps {
  children: React.ReactNode;
  appName: string;
  navGroups: NavGroup[];
}

const COLLAPSE_KEY = 'sidebar-collapsed';

/**
 * Dashboard shell for the admin & seller apps: a collapsible left sidebar with
 * grouped navigation, a slim top bar (page title + notifications + account),
 * and a slide-out drawer on mobile.
 */
export default function SidebarLayout({ children, appName, navGroups }: SidebarLayoutProps) {
  const { user, logout } = useAuthStore();
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSE_KEY) === '1');
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => { localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0'); }, [collapsed]);
  // Close the mobile drawer whenever the route changes.
  useEffect(() => { setMobileOpen(false); }, [location.pathname]);

  const isActive = (to: string) =>
    location.pathname === to || (to !== '/' && location.pathname.startsWith(to + '/'));

  const allItems = navGroups.flatMap((g) => g.items);
  const currentTitle = allItems.find((i) => isActive(i.to))?.label ?? appName;

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const sidebarBody = (
    <div className="flex flex-col h-full">
      {/* Brand */}
      <div className={`flex items-center h-16 shrink-0 border-b border-gray-800 ${collapsed ? 'justify-center px-2' : 'gap-2 px-5'}`}>
        <span className="flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-to-br from-blue-500 to-violet-600 text-white text-lg font-bold shadow-sm shrink-0">
          ⚡
        </span>
        {!collapsed && <span className="font-bold text-white text-base tracking-tight truncate">{appName}</span>}
      </div>

      {/* Nav groups */}
      <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-5">
        {navGroups.map((group, gi) => (
          <div key={gi}>
            {group.label && !collapsed && (
              <p className="px-3 pb-1.5 text-[10px] font-semibold uppercase tracking-wider text-gray-500">
                {group.label}
              </p>
            )}
            <div className="space-y-0.5">
              {group.items.map((item) => {
                const active = isActive(item.to);
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    title={collapsed ? item.label : undefined}
                    className={`flex items-center rounded-lg text-sm font-medium transition-colors ${
                      collapsed ? 'justify-center px-2 py-2.5' : 'gap-3 px-3 py-2'
                    } ${
                      active
                        ? 'bg-blue-600 text-white shadow-sm'
                        : 'text-gray-300 hover:text-white hover:bg-gray-800'
                    }`}
                  >
                    {item.iconKey && <Icon name={item.iconKey} className="w-5 h-5 shrink-0" />}
                    {!collapsed && <span className="truncate">{item.label}</span>}
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      {/* Account / logout */}
      <div className="shrink-0 border-t border-gray-800 p-3">
        <div className={`flex items-center ${collapsed ? 'justify-center' : 'gap-3 px-1'}`}>
          <span className="w-9 h-9 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-sm font-bold uppercase shrink-0">
            {user?.username?.charAt(0) ?? '?'}
          </span>
          {!collapsed && (
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-white truncate">{user?.username}</p>
              <p className="text-xs text-gray-400 truncate">{user?.role}</p>
            </div>
          )}
          {!collapsed && (
            <button
              onClick={handleLogout}
              title="Đăng xuất"
              className="p-2 rounded-lg text-gray-400 hover:text-red-400 hover:bg-gray-800 transition-colors"
            >
              <Icon name="logout" className="w-5 h-5" />
            </button>
          )}
        </div>
        {collapsed && (
          <button
            onClick={handleLogout}
            title="Đăng xuất"
            className="mt-2 w-full flex justify-center p-2 rounded-lg text-gray-400 hover:text-red-400 hover:bg-gray-800 transition-colors"
          >
            <Icon name="logout" className="w-5 h-5" />
          </button>
        )}
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Desktop sidebar — fixed */}
      <aside
        className={`hidden md:flex flex-col fixed inset-y-0 left-0 z-30 bg-gray-900 transition-[width] duration-200 ${
          collapsed ? 'w-[72px]' : 'w-64'
        }`}
      >
        {sidebarBody}
        {/* Collapse toggle */}
        <button
          onClick={() => setCollapsed((v) => !v)}
          className="absolute -right-3 top-20 w-6 h-6 rounded-full bg-white border border-gray-200 shadow flex items-center justify-center text-gray-500 hover:text-gray-900 transition-colors"
          aria-label={collapsed ? 'Mở rộng' : 'Thu gọn'}
        >
          <Icon name="chevronLeft" className={`w-4 h-4 transition-transform ${collapsed ? 'rotate-180' : ''}`} />
        </button>
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="md:hidden fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-64 bg-gray-900 shadow-2xl animate-in slide-in-from-left duration-200">
            {sidebarBody}
          </aside>
        </div>
      )}

      {/* Main column */}
      <div className={`transition-[padding] duration-200 ${collapsed ? 'md:pl-[72px]' : 'md:pl-64'}`}>
        {/* Top bar */}
        <header className="sticky top-0 z-20 h-16 bg-white/95 backdrop-blur border-b border-gray-200 flex items-center gap-3 px-4 sm:px-6">
          <button
            onClick={() => setMobileOpen(true)}
            className="md:hidden p-2 -ml-2 rounded-lg text-gray-500 hover:text-gray-900 hover:bg-gray-100 transition-colors"
            aria-label="Menu"
          >
            <Icon name="menu" className="w-6 h-6" />
          </button>
          <h1 className="text-base font-semibold text-gray-900 truncate">{currentTitle}</h1>
          <div className="ml-auto flex items-center gap-2">
            <NotificationBell />
          </div>
        </header>

        <main>{children}</main>
      </div>
    </div>
  );
}
