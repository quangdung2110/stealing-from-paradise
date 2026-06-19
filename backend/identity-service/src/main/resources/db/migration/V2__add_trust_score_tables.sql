CREATE TABLE trust_score_events_config (
    id          BIGSERIAL    PRIMARY KEY,
    event_code  VARCHAR(60)  NOT NULL UNIQUE,
    delta       INT          NOT NULL,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE trust_score_logs (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    delta       INT          NOT NULL,
    event_code  VARCHAR(60)  REFERENCES trust_score_events_config(event_code),
    reason      TEXT,
    changed_by  VARCHAR(20)  NOT NULL,   -- ADMIN | SYSTEM
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE loyalty_accounts (
    id                   BIGSERIAL   PRIMARY KEY,
    user_id              BIGINT      NOT NULL UNIQUE REFERENCES users(id),
    total_earned_points  INT         NOT NULL DEFAULT 0,
    available_points     INT         NOT NULL DEFAULT 0,
    used_points          INT         NOT NULL DEFAULT 0,
    expired_points       INT         NOT NULL DEFAULT 0,
    version              INT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE point_transactions (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    order_id        BIGINT,
    order_code      VARCHAR(50),
    delta           INT          NOT NULL,
    remaining_delta INT          NOT NULL,
    type            VARCHAR(20)  NOT NULL,   -- EARNED | USED | EXPIRED | REFUNDED
    status          VARCHAR(20)  NOT NULL,   -- PENDING | CONFIRMED
    balance_after   INT          NOT NULL,
    note            TEXT,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_point_txn_order_earned UNIQUE (order_id, type) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_trust_score_logs_user    ON trust_score_logs(user_id);
CREATE INDEX idx_point_transactions_user   ON point_transactions(user_id);
CREATE INDEX idx_point_transactions_order  ON point_transactions(order_id);

