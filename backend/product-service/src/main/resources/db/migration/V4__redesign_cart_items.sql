-- Align cart_items with the redesigned CartItem entity:
-- composite PK (customer_id, variant_id), seller_id snapshot, no cart_id/soft-delete.
-- The entity changed without a migration, leaving dev databases on the old shape
-- (id/cart_id/deleted_at, no customer_id) and breaking every cart operation.
-- Carts are ephemeral; rebuilding the table instead of migrating rows.

DROP TABLE IF EXISTS cart_items;

CREATE TABLE cart_items (
    customer_id            BIGINT        NOT NULL,
    variant_id             UUID          NOT NULL,
    quantity               INT           NOT NULL,
    price_snapshot         NUMERIC(18,2) NOT NULL,
    variant_name_snapshot  VARCHAR(255)  NOT NULL,
    variant_image_snapshot VARCHAR(255),
    seller_id              BIGINT        NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, variant_id)
);

CREATE INDEX idx_cart_items_customer ON cart_items (customer_id);
CREATE INDEX idx_cart_items_variant  ON cart_items (variant_id);
