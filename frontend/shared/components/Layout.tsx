import Navbar from './Navbar';
import Footer from './Footer';
import BottomTabBar from './BottomTabBar';
import type { NavItem, NavGroup } from './navConfig';

interface LayoutProps {
  children: React.ReactNode;
  appName: string;
  /** Top-priority destinations shown inline on the desktop bar. */
  primaryItems?: NavItem[];
  /** Secondary destinations grouped inside the account dropdown. */
  menuGroups?: NavGroup[];
  /** Mobile bottom-tab destinations (customer app). When set, a bottom tab bar renders. */
  bottomTabItems?: NavItem[];
  showSearch?: boolean;
  showCart?: boolean;
}

export default function Layout({
  children,
  appName,
  primaryItems,
  menuGroups,
  bottomTabItems,
  showSearch,
  showCart,
}: LayoutProps) {
  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      <Navbar
        appName={appName}
        primaryItems={primaryItems}
        menuGroups={menuGroups}
        showSearch={showSearch}
        showCart={showCart}
      />
      {/* Pad the bottom on mobile so the fixed tab bar never covers content. */}
      <main className={`flex-1 ${bottomTabItems ? 'pb-16 md:pb-0' : ''}`}>
        {children}
      </main>
      <Footer appName={appName} />
      {bottomTabItems && <BottomTabBar items={bottomTabItems} />}
    </div>
  );
}
