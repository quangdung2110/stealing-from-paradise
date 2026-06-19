-- Axon JPA tables (TokenStore / SagaStore).
-- IF NOT EXISTS: on dev machines Hibernate ddl-auto may have created these
-- tables before this migration was introduced — the migration must tolerate that.

-- 1. Bảng token_entry: Lưu vết tiến độ của Processor
CREATE TABLE IF NOT EXISTS token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INT NOT NULL,
    owner VARCHAR(255),
    timestamp VARCHAR(255) NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

CREATE INDEX IF NOT EXISTS idx_token_owner ON token_entry (owner);

-- 2. Bảng saga_entry: Lưu trạng thái của Saga
CREATE TABLE IF NOT EXISTS saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    saga_type VARCHAR(255),
    serialized_saga BYTEA,
    PRIMARY KEY (saga_id)
);

-- 3. Bảng association_value_entry: Lưu ánh xạ giữa Saga và các định danh
CREATE TABLE IF NOT EXISTS association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_saga_association ON association_value_entry (association_key, association_value);
CREATE INDEX IF NOT EXISTS idx_saga_id_type ON association_value_entry (saga_id, saga_type);
