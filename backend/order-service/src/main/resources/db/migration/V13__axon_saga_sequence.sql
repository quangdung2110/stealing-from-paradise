-- Axon JpaSagaStore expects a sequence literally named "association_value_entry_seq"
-- to allocate ids for the AssociationValueEntry table. Postgres' BIGSERIAL on `id`
-- creates a sequence named "association_value_entry_id_seq" (column-suffixed) which
-- Axon never queries. Without this sequence, every saga that adds an association
-- (i.e. every @StartSaga) fails with: relation "association_value_entry_seq" does not exist
-- and the saga is replayed forever, blocking all downstream payment processing.
CREATE SEQUENCE IF NOT EXISTS association_value_entry_seq AS BIGINT START WITH 1 INCREMENT BY 1;
SELECT setval('association_value_entry_seq',
              GREATEST(1, COALESCE((SELECT MAX(id) FROM association_value_entry), 0)));
