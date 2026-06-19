-- ============================================================
-- Migration V5: Create Axon JPA Token Store / Saga Store tables
-- ============================================================
-- These tables are NOT auto-created when spring.jpa.hibernate.ddl-auto=none.
-- Flyway manages them here so the schema matches what Axon 4.x expects.
--
-- Safety: DROP + CREATE ensures the schema is always correct, regardless
-- of whether a prior V4 (BIGINT timestamp) was previously applied.
-- Axon will re-create tokens on restart if needed.
-- ============================================================

-- Drop in correct order (FK if any, then tables)
DROP TABLE IF EXISTS token_entry CASCADE;
DROP TABLE IF EXISTS saga_entry CASCADE;

-- Create token_entry with correct VARCHAR(255) timestamp for Axon 4.x
CREATE TABLE token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment        INT          NOT NULL,
    owner          VARCHAR(255),
    timestamp      VARCHAR(255) NOT NULL,
    token          BYTEA,
    token_type     VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

CREATE INDEX idx_token_owner ON token_entry (owner);

-- Create saga_entry for saga state storage
CREATE TABLE saga_entry (
    saga_id          VARCHAR(255) NOT NULL,
    revision         VARCHAR(255),
    saga_type        VARCHAR(255),
    serialized_saga  BYTEA,
    PRIMARY KEY (saga_id)
);
