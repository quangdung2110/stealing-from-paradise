-- 1. Bảng token_entry: Lưu vết tiến độ của Processor (Cái này đang gây lỗi cho bạn)
CREATE TABLE IF NOT EXISTS token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    owner VARCHAR(255),
    timestamp VARCHAR(255) NOT NULL,
    token BYTEA, -- Axon 4.x dùng BYTEA để lưu serialized token
    token_type VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

-- 2. Bảng saga_entry: Lưu trạng thái của Saga
CREATE TABLE IF NOT EXISTS saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    saga_type VARCHAR(255),
    serialized_saga BYTEA,
    PRIMARY KEY (saga_id)
);

-- 3. Bảng association_value_entry: Lưu ánh xạ giữa Saga và các định danh (OrderId, PaymentId, ...)
CREATE TABLE IF NOT EXISTS association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255)
);

-- Index cực kỳ quan trọng để Saga tìm kiếm nhanh khi có hàng vạn giao dịch Flash Sale
CREATE INDEX idx_saga_association ON association_value_entry (association_key, association_value);
CREATE INDEX idx_saga_id_type ON association_value_entry (saga_id, saga_type);