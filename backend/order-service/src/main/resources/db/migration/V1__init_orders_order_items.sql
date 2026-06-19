CREATE TABLE parent_orders (
    id               BIGSERIAL       PRIMARY KEY,
    user_id          BIGINT          NOT NULL,
    total_amt        DECIMAL(15,2)   NOT NULL,
    loyalty_discount DECIMAL(15,2)   NOT NULL DEFAULT 0,
    final_amt        DECIMAL(15,2)   NOT NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
    id               BIGSERIAL       PRIMARY KEY,
    parent_order_id  BIGINT          NOT NULL REFERENCES parent_orders(id),
    seller_id        BIGINT          NOT NULL,
    order_code       VARCHAR(50)     NOT NULL UNIQUE,
    user_id          BIGINT          NOT NULL,
    total_amt        DECIMAL(15,2)   NOT NULL,
    final_amt        DECIMAL(15,2)   NOT NULL,
    status           VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    cancelled_by     VARCHAR(20),
    cancel_reason    TEXT,
    is_flash_sale    BOOLEAN         NOT NULL DEFAULT FALSE,
    shipping_address JSONB           NOT NULL,
    tracking_number  VARCHAR(100),
    shipping_deadline TIMESTAMP,
    version          INT             NOT NULL DEFAULT 0,
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE order_items (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id),
    sku_code        VARCHAR(100)    NOT NULL,
    variant_id      VARCHAR(24),
    name_snapshot   VARCHAR(255)    NOT NULL,
    image_snapshot  VARCHAR(500),
    price_snapshot  DECIMAL(15,2)   NOT NULL,
    quantity        INT             NOT NULL,
    refunded_quantity INT           NOT NULL DEFAULT 0,
    fs_item_id      BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_id        ON orders(user_id);
CREATE INDEX idx_orders_seller_id       ON orders(seller_id);
CREATE INDEX idx_orders_parent_order_id ON orders(parent_order_id);
CREATE INDEX idx_orders_status          ON orders(status);
CREATE INDEX idx_order_items_order_id   ON order_items(order_id);

