/**
 * Seller order-action business rules.
 *
 * Pure, DOM-free predicates and validators that encode the seller-facing
 * order use cases. Keeping them here (instead of inline in the pages) lets the
 * UI stay declarative and lets the rules be unit-tested in isolation.
 *
 * References:
 *  - UC-ORDER-004 Ship Order
 *  - UC-ORDER-006 Flow B Return-To-Sender (RTS)
 *  - UC-ORDER-008 Seller Cancel Order
 *  - BR-ORDER-026 (cancel reason length)
 */
import type { OrderStatus } from '@shared/api/order.api';

/** BR-ORDER-026 — seller cancel reason must be 10–500 characters. */
export const CANCEL_REASON_MIN = 10;
export const CANCEL_REASON_MAX = 500;

/** UC-ORDER-006 Flow B — RTS requires between 1 and 5 evidence images. */
export const RTS_MIN_IMAGES = 1;
export const RTS_MAX_IMAGES = 5;

/**
 * UC-ORDER-004 (P2): a sub-order can be shipped only while it is PAID.
 * Providing tracking transitions PAID → SHIPPING.
 */
export function canShip(status: OrderStatus): boolean {
  return status === 'PAID';
}

/**
 * UC-ORDER-008 (P2, P3): the seller may cancel a PAID order only *before* it
 * ships — i.e. while `tracking_number` is still null. Once SHIPPING the seller
 * must use Return-To-Sender instead (direct cancel is forbidden in transit).
 */
export function canCancel(status: OrderStatus, trackingNumber?: string | null): boolean {
  return status === 'PAID' && !trackingNumber;
}

/**
 * UC-ORDER-006 Flow B (P2): Return-To-Sender is allowed only while the order is
 * SHIPPING (carrier returned the package). It triggers an automatic full refund.
 */
export function canReturnToSender(status: OrderStatus): boolean {
  return status === 'SHIPPING';
}

export interface ValidationResult {
  valid: boolean;
  /** Human-readable Vietnamese message, present only when `valid` is false. */
  error?: string;
}

/** BR-ORDER-026 — validate a seller cancel reason (trimmed length 10–500). */
export function validateCancelReason(reason: string): ValidationResult {
  const len = reason.trim().length;
  if (len < CANCEL_REASON_MIN) {
    return { valid: false, error: `Lý do phải có tối thiểu ${CANCEL_REASON_MIN} ký tự.` };
  }
  if (len > CANCEL_REASON_MAX) {
    return { valid: false, error: `Lý do tối đa ${CANCEL_REASON_MAX} ký tự.` };
  }
  return { valid: true };
}

/**
 * UC-ORDER-006 Flow B — RTS requires a mandatory return tracking number plus
 * 1–5 evidence images of the returned package.
 */
export function validateRtsForm(returnTracking: string, imageCount: number): ValidationResult {
  if (!returnTracking.trim()) {
    return { valid: false, error: 'Vui lòng nhập mã vận đơn hoàn.' };
  }
  if (imageCount < RTS_MIN_IMAGES) {
    return { valid: false, error: 'Cần ít nhất 1 ảnh bằng chứng tình trạng hàng hoàn.' };
  }
  if (imageCount > RTS_MAX_IMAGES) {
    return { valid: false, error: `Tối đa ${RTS_MAX_IMAGES} ảnh bằng chứng.` };
  }
  return { valid: true };
}
