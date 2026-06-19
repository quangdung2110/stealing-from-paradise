-- Bảng association_value_entry: Lưu ánh xạ giữa Saga và các định danh (OrderId, PaymentId, ...)
-- token_entry và saga_entry được Axon tự quản lý qua JpaTokenStore/JpaSagaStore
-- và sẽ được Hibernate tạo đúng kiểu (OID cho byte[]) khi ứng dụng khởi động.
CREATE TABLE IF NOT EXISTS association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255)
);

CREATE INDEX idx_saga_association ON association_value_entry (association_key, association_value);
CREATE INDEX idx_saga_id_type ON association_value_entry (saga_id, saga_type);
