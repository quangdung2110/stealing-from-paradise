import { describe, it, expect } from 'vitest';
import {
  canShip,
  canCancel,
  canReturnToSender,
  validateCancelReason,
  validateRtsForm,
  CANCEL_REASON_MIN,
  CANCEL_REASON_MAX,
  RTS_MAX_IMAGES,
} from '../orderActions';
import type { OrderStatus } from '@shared/api/order.api';

const ALL_STATUSES: OrderStatus[] = [
  'PENDING', 'PAID', 'SHIPPING', 'DELIVERED',
  'CANCELLED', 'RETURNED', 'PARTIALLY_REFUNDED', 'REFUNDED',
];

describe('canShip (UC-ORDER-004)', () => {
  it('allows shipping only for PAID orders', () => {
    expect(canShip('PAID')).toBe(true);
  });

  it('forbids shipping for every non-PAID status', () => {
    ALL_STATUSES.filter(s => s !== 'PAID').forEach(s => {
      expect(canShip(s)).toBe(false);
    });
  });
});

describe('canCancel (UC-ORDER-008)', () => {
  it('allows cancel for PAID orders that have not shipped', () => {
    expect(canCancel('PAID', null)).toBe(true);
    expect(canCancel('PAID', undefined)).toBe(true);
    expect(canCancel('PAID', '')).toBe(true);
  });

  it('forbids cancel once a tracking number exists (already shipping)', () => {
    expect(canCancel('PAID', 'VN123')).toBe(false);
  });

  it('forbids cancel for PENDING (seller has no claim until paid)', () => {
    expect(canCancel('PENDING', null)).toBe(false);
  });

  it('forbids cancel for SHIPPING/DELIVERED/etc', () => {
    ['SHIPPING', 'DELIVERED', 'CANCELLED', 'RETURNED', 'REFUNDED'].forEach(s => {
      expect(canCancel(s as OrderStatus, null)).toBe(false);
    });
  });
});

describe('canReturnToSender (UC-ORDER-006 Flow B)', () => {
  it('allows RTS only while SHIPPING', () => {
    expect(canReturnToSender('SHIPPING')).toBe(true);
  });

  it('forbids RTS for every non-SHIPPING status', () => {
    ALL_STATUSES.filter(s => s !== 'SHIPPING').forEach(s => {
      expect(canReturnToSender(s)).toBe(false);
    });
  });
});

describe('validateCancelReason (BR-ORDER-026)', () => {
  it('rejects reasons shorter than the minimum', () => {
    const r = validateCancelReason('a'.repeat(CANCEL_REASON_MIN - 1));
    expect(r.valid).toBe(false);
    expect(r.error).toBeTruthy();
  });

  it('rejects whitespace-padded reasons that are too short once trimmed', () => {
    expect(validateCancelReason('   short   ').valid).toBe(false);
  });

  it('accepts a reason exactly at the minimum length', () => {
    expect(validateCancelReason('a'.repeat(CANCEL_REASON_MIN)).valid).toBe(true);
  });

  it('rejects reasons longer than the maximum', () => {
    expect(validateCancelReason('a'.repeat(CANCEL_REASON_MAX + 1)).valid).toBe(false);
  });
});

describe('validateRtsForm (UC-ORDER-006 Flow B)', () => {
  it('requires a return tracking number', () => {
    expect(validateRtsForm('', 1).valid).toBe(false);
    expect(validateRtsForm('   ', 2).valid).toBe(false);
  });

  it('requires at least one evidence image', () => {
    expect(validateRtsForm('VN999', 0).valid).toBe(false);
  });

  it('rejects more than the max allowed images', () => {
    expect(validateRtsForm('VN999', RTS_MAX_IMAGES + 1).valid).toBe(false);
  });

  it('accepts a tracking number with 1..max images', () => {
    expect(validateRtsForm('VN999', 1).valid).toBe(true);
    expect(validateRtsForm('VN999', RTS_MAX_IMAGES).valid).toBe(true);
  });
});
