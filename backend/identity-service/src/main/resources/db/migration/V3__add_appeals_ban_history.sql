CREATE TABLE user_ban_history (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    action       VARCHAR(20)  NOT NULL,   -- LOCKED | UNLOCKED
    reason       TEXT,
    performed_by VARCHAR(20)  NOT NULL,   -- ADMIN | SYSTEM
    admin_id     BIGINT       REFERENCES users(id),
    locked_until TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE appeals (
    id                  BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id),
    trust_score_log_id  BIGINT       NOT NULL REFERENCES trust_score_logs(id),
    reason              TEXT         NOT NULL,
    evidence_urls       JSONB,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by         BIGINT       REFERENCES users(id),
    admin_note          TEXT,
    reviewed_at         TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appeals_user_id   ON appeals(user_id);
CREATE INDEX idx_appeals_status     ON appeals(status);

