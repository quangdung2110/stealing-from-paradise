-- order_items.variant_id was VARCHAR(24) but Product Service emits UUIDs (36 chars)
-- for the v4 cart redesign. Every checkout fails with
--   "value too long for type character varying(24)"
-- and the order saga never starts. Widen to 64 to match product.product_variants.id.
ALTER TABLE orders.order_items ALTER COLUMN variant_id TYPE VARCHAR(64);
