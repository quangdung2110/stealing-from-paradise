/**
 * Seller product lifecycle rules.
 *
 * Pure predicates encoding which actions are valid for a product in each state,
 * mirroring the DRAFT → PENDING → APPROVED → ACTIVE/INACTIVE/REJECTED
 * lifecycle (UC-PRODUCT-003/013/014/015). Extracted from ProductManagementPage
 * so the per-row action buttons stay declarative and the rules are unit-testable.
 */
import type { SellerProduct } from '@shared/api/seller.api';

export type ProductStatus = SellerProduct['status'];

/** Edit is always available to the owning seller. */
export function canEdit(_status: ProductStatus): boolean {
  return true;
}

/** A DRAFT can be submitted for admin review. */
export function canSubmit(status: ProductStatus): boolean {
  return status === 'DRAFT';
}

/** An APPROVED (or previously hidden) product can be published/listed. */
export function canPublish(status: ProductStatus): boolean {
  return status === 'APPROVED' || status === 'UNPUBLISHED' || status === 'INACTIVE' || status === 'OUT_OF_STOCK';
}

/** A live product can be hidden again. */
export function canUnpublish(status: ProductStatus): boolean {
  return status === 'PUBLISHED' || status === 'ACTIVE' || status === 'OUT_OF_STOCK';
}

/** Only DRAFT or REJECTED products can be hard-deleted. */
export function canDelete(status: ProductStatus): boolean {
  return status === 'DRAFT' || status === 'REJECTED';
}
