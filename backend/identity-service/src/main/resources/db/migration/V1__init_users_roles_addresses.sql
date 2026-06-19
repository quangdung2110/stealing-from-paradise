-- identity-service: V1__init_users_roles_addresses.sql
CREATE TABLE users (
    id                          BIGSERIAL        PRIMARY KEY,
    username                    VARCHAR(50)      NOT NULL UNIQUE,
    email                       VARCHAR(255)     NOT NULL UNIQUE,
    phone                       VARCHAR(20)      UNIQUE,
    password                    VARCHAR(255)     NOT NULL,
    full_name                   VARCHAR(100),
    avatar_url                  VARCHAR(500),
    status                      VARCHAR(20)      NOT NULL DEFAULT 'ACTIVE',
    trust_score                 INT              NOT NULL DEFAULT 80,
    locked_until                TIMESTAMP,
    lock_reason                 TEXT,
    appeal_count                INT              NOT NULL DEFAULT 0,
    product_posting_suspended   BOOLEAN          NOT NULL DEFAULT FALSE,
    last_cancellation_penalty_at TIMESTAMP,
    last_warning_at             TIMESTAMP,
    last_posting_suspension_at  TIMESTAMP,
    reward_10_orders_accumulated INT             NOT NULL DEFAULT 0,
    version                     INT              NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP        NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_name   VARCHAR(20)  NOT NULL,   -- BUYER | SELLER | ADMIN
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE addresses (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    province_id  INT         NOT NULL,
    district_id  INT         NOT NULL,
    full_address TEXT        NOT NULL,
    is_default   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_roles_user_id    ON roles(user_id);
CREATE INDEX idx_addresses_user_id ON addresses(user_id);

