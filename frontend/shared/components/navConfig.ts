import type { IconName } from './icons';

/** A single navigation destination. */
export interface NavItem {
  label: string;
  to: string;
  iconKey?: IconName;
  /** Legacy emoji icon (still accepted for backward compatibility). */
  icon?: string;
  /** Live counter badge (e.g. cart item count). 'cart' resolves at render time. */
  badge?: 'cart';
}

/** A labelled group of nav items, used in the user dropdown and the sidebar. */
export interface NavGroup {
  label?: string;
  items: NavItem[];
}
