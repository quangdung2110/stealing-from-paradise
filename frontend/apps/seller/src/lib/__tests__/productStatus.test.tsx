import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PRODUCT_STATUS_META, getProductStatusMeta, ProductStatusBadge } from '../productStatus';
import type { ProductStatus } from '../productActions';

const ALL: ProductStatus[] = [
  'DRAFT',
  'PENDING',
  'APPROVED',
  'REJECTED',
  'UNPUBLISHED',
  'PUBLISHED',
  'ACTIVE',
  'INACTIVE',
  'OUT_OF_STOCK',
];

describe('PRODUCT_STATUS_META', () => {
  it('has label + chip styles for every product status', () => {
    ALL.forEach(s => {
      const m = PRODUCT_STATUS_META[s];
      expect(m.label.length).toBeGreaterThan(0);
      expect(m.bg).toMatch(/^bg-/);
      expect(m.color).toMatch(/^text-/);
    });
  });
});

describe('getProductStatusMeta', () => {
  it('resolves a known status', () => {
    expect(getProductStatusMeta('PUBLISHED').label).toBe('Đang bán');
  });

  it('falls back to the raw value for an unknown status', () => {
    expect(getProductStatusMeta('ZZZ').label).toBe('ZZZ');
  });
});

describe('ProductStatusBadge', () => {
  it('renders the localized label', () => {
    render(<ProductStatusBadge status="PENDING" />);
    expect(screen.getByText('Chờ duyệt')).toBeInTheDocument();
  });
});
