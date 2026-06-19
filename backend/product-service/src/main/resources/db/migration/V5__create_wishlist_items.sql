-- Wishlist: customer's favorite products.
-- Composite PK (customer_id, product_id) guarantees one row per customer per product,
-- mirroring the cart_items design.
CREATE TABLE wishlist_items (
    customer_id BIGINT    NOT NULL,
    product_id  UUID      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT pk_wishlist_items PRIMARY KEY (customer_id, product_id),
    CONSTRAINT fk_wishlist_items_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE CASCADE
);

-- Wishlist page is always "my items, newest first".
CREATE INDEX idx_wishlist_items_customer_created
    ON wishlist_items (customer_id, created_at DESC);
