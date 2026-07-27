-- Envers audit table sync. The Stock entity is @Audited, so every new/changed column that is NOT
-- @NotAudited must also exist on stock_aud — otherwise Envers' audit insert fails and rolls back the
-- whole (transactional) stocking operation, so no stock AND no assets get created.
--
-- ddl-auto=update used to keep stock_aud in sync automatically; with Flyway-owned schema we do it
-- explicitly here. Audit columns are plain + nullable (no unique constraints — history keeps many
-- revisions of the same row). Guarded so it's safe whether or not the columns already exist.

SET @schema := DATABASE();

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock_aud' AND COLUMN_NAME = 'po_number'),
    'ALTER TABLE stock_aud ADD COLUMN po_number VARCHAR(255) NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock_aud' AND COLUMN_NAME = 'order_date'),
    'ALTER TABLE stock_aud ADD COLUMN order_date DATETIME(6) NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock_aud' AND COLUMN_NAME = 'delivery_date'),
    'ALTER TABLE stock_aud ADD COLUMN delivery_date DATETIME(6) NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock_aud' AND COLUMN_NAME = 'invoice_date'),
    'ALTER TABLE stock_aud ADD COLUMN invoice_date DATETIME(6) NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock_aud' AND COLUMN_NAME = 'close_short_reason'),
    'ALTER TABLE stock_aud ADD COLUMN close_short_reason VARCHAR(255) NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock_aud' AND COLUMN_NAME = 'idempotency_key'),
    'ALTER TABLE stock_aud ADD COLUMN idempotency_key VARCHAR(255) NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
