-- V12: Restore parent_order_id to seller_transfers (dropped in V7).
-- Required for findAllByParentOrderId query used by:
--   SellerTransferService.createSellerTransfers() (payment success)
--   PaymentQueryService.buildTransactionDetailResponse() (transaction detail)

ALTER TABLE seller_transfers
    ADD COLUMN IF NOT EXISTS parent_order_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_seller_transfers_parent_order ON seller_transfers(parent_order_id);
