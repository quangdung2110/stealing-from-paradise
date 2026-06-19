CREATE SCHEMA IF NOT EXISTS refund;

CREATE TABLE refund.refunds (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT,
    group_ref UUID,
    type VARCHAR(255) NOT NULL,
    initiated_by VARCHAR(255) NOT NULL,
    refund_reason_type VARCHAR(255),
    amount DECIMAL NOT NULL,
    reason TEXT,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    evidence_images JSONB,
    reject_reason TEXT,
    admin_note TEXT,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    refund_ref VARCHAR(255),
    raw_response JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_order ON refund.refunds(order_id);

CREATE TABLE refund.refund_items (
    id BIGSERIAL PRIMARY KEY,
    refund_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    refund_amount DECIMAL,
    item_reason TEXT,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    return_tracking_number VARCHAR(255),
    return_evidence_images JSONB,
    returned_at TIMESTAMP
);

-- Axon Framework tables (từ payment-service V2)
CREATE TABLE IF NOT EXISTS refund.token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    owner VARCHAR(255),
    timestamp VARCHAR(255) NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

CREATE TABLE IF NOT EXISTS refund.saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    saga_type VARCHAR(255),
    serialized_saga BYTEA,
    PRIMARY KEY (saga_id)
);

CREATE TABLE IF NOT EXISTS refund.association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255)
);

CREATE INDEX idx_refund_saga_association ON refund.association_value_entry (association_key, association_value);
CREATE INDEX idx_refund_saga_id_type ON refund.association_value_entry (saga_id, saga_type);
