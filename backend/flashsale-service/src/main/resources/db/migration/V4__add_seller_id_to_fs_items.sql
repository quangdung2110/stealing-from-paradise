ALTER TABLE fs_items
    ADD COLUMN IF NOT EXISTS seller_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_fs_items_seller_id ON fs_items(seller_id);
