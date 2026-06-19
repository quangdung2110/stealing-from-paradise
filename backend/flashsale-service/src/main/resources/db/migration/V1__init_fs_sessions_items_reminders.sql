CREATE TABLE fs_sessions (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    start_time  TIMESTAMP    NOT NULL,
    end_time    TIMESTAMP    NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'UPCOMING',
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE fs_items (
    id             BIGSERIAL       PRIMARY KEY,
    session_id     BIGINT          NOT NULL REFERENCES fs_sessions(id),
    sku_code       VARCHAR(100)    NOT NULL,
    flash_price    DECIMAL(15,2)   NOT NULL,
    flash_stock    INT             NOT NULL,
    limit_per_user INT             NOT NULL DEFAULT 1,
    sold_qty       INT             NOT NULL DEFAULT 0,
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    version        INT             NOT NULL DEFAULT 0,
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE fs_reminders (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    session_id  BIGINT      NOT NULL REFERENCES fs_sessions(id),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fs_reminders UNIQUE (user_id, session_id)
);

CREATE INDEX idx_fs_items_session_id ON fs_items(session_id);
CREATE INDEX idx_fs_sessions_status  ON fs_sessions(status);

