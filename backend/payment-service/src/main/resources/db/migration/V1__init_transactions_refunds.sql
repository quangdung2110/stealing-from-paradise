CREATE TABLE seller_stripe_accounts (
    id                  BIGSERIAL    PRIMARY KEY,
    seller_id           BIGINT       NOT NULL UNIQUE,
    stripe_account_id   VARCHAR(100) NOT NULL,
    account_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    charges_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    payouts_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    details_submitted   BOOLEAN      NOT NULL DEFAULT FALSE,
    onboarding_url      TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions (
    id                     BIGSERIAL       PRIMARY KEY,
    parent_order_id        BIGINT          NOT NULL,
    amount                 DECIMAL(15,2)   NOT NULL,
    method                 VARCHAR(20)     NOT NULL DEFAULT 'STRIPE',
    trans_ref              VARCHAR(200),
    stripe_transfer_id     VARCHAR(200),
    application_fee_amount DECIMAL(15,2),
    stripe_connect_mode    VARCHAR(20),
    status                 VARCHAR(30)     NOT NULL,
    raw_response           JSONB,
    pay_at                 TIMESTAMP,
    created_at             TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE refunds (
    id                  BIGSERIAL       PRIMARY KEY,
    transaction_id      BIGINT          NOT NULL REFERENCES transactions(id),
    order_id            BIGINT          NOT NULL,
    group_ref           UUID,
    type                VARCHAR(10)     NOT NULL,
    initiated_by        VARCHAR(20)     NOT NULL,
    refund_reason_type  VARCHAR(30),
    amount              DECIMAL(15,2)   NOT NULL,
    reason              TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    evidence_images     JSONB,
    reject_reason       TEXT,
    admin_note          TEXT,
    adjust_amount       DECIMAL(15,2),
    reviewed_by         BIGINT,
    reviewed_at         TIMESTAMP,
    refund_ref          VARCHAR(200),
    raw_response        JSONB,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE refund_items (
    id                      BIGSERIAL       PRIMARY KEY,
    refund_id               BIGINT          NOT NULL REFERENCES refunds(id),
    item_id                 BIGINT          NOT NULL,
    quantity                INT             NOT NULL,
    refund_amount           DECIMAL(15,2)   NOT NULL,
    item_reason             TEXT,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    return_tracking_number  VARCHAR(100),
    return_evidence_images  JSONB,
    returned_at             TIMESTAMP
);

CREATE TABLE seller_transfers (
    id                  BIGSERIAL       PRIMARY KEY,
    order_id            BIGINT          NOT NULL,
    seller_id           BIGINT          NOT NULL,
    transfer_amount     DECIMAL(15,2)   NOT NULL,
    stripe_transfer_id  VARCHAR(200),
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_parent_order ON transactions(parent_order_id);
CREATE INDEX idx_refunds_order_id           ON refunds(order_id);
CREATE INDEX idx_seller_transfers_order_id  ON seller_transfers(order_id);

