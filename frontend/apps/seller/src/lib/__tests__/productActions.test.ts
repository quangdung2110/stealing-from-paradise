import { describe, it, expect } from 'vitest';
import { canEdit, canSubmit, canPublish, canUnpublish, canDelete, type ProductStatus } from '../productActions';

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

/** Assert a predicate is true for exactly `expected` and false for the rest. */
function onlyFor(fn: (s: ProductStatus) => boolean, expected: ProductStatus[]) {
  ALL.forEach(s => expect(fn(s)).toBe(expected.includes(s)));
}

describe('productActions (UC-PRODUCT lifecycle)', () => {
  it('canEdit is allowed in every state', () => {
    ALL.forEach(s => expect(canEdit(s)).toBe(true));
  });

  it('canSubmit only for DRAFT', () => onlyFor(canSubmit, ['DRAFT']));

  it('canPublish only for approved or currently hidden sellable states', () =>
    onlyFor(canPublish, ['APPROVED', 'UNPUBLISHED', 'INACTIVE', 'OUT_OF_STOCK']));

  it('canUnpublish only for currently visible sellable states', () =>
    onlyFor(canUnpublish, ['PUBLISHED', 'ACTIVE', 'OUT_OF_STOCK']));

  it('canDelete only for DRAFT or REJECTED', () => onlyFor(canDelete, ['DRAFT', 'REJECTED']));

  it('PENDING (awaiting admin) exposes no lifecycle action besides edit', () => {
    expect(canSubmit('PENDING')).toBe(false);
    expect(canPublish('PENDING')).toBe(false);
    expect(canUnpublish('PENDING')).toBe(false);
    expect(canDelete('PENDING')).toBe(false);
  });
});
