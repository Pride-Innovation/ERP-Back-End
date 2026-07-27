-- Guarantees the named unique constraints that were previously created only by ddl-auto=update.
-- That mode does NOT reliably create/alter constraints across vendors, and now that schema is
-- owned by Flyway (ddl-auto=none) nothing else would create them. Each is added only if missing.
--
-- Covers the constraints flagged in the application.properties comment plus the store-balance
-- uniqueness that StoreSeeder#relaxLegacyBalanceUniqueness handled in Java at startup (that Java
-- safety net remains, but is now redundant — Flyway runs first and leaves nothing for it to do).
--
-- If any ADD CONSTRAINT fails, the table already holds rows that violate it: de-duplicate the
-- offending rows, then re-run. (A halted migration surfacing bad data is intentional.)

SET @schema := DATABASE();

-- ── store_container: uk_store_location_type_department_courier ────────────────────────────────
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'store_container'
      AND INDEX_NAME = 'uk_store_location_type_department_courier'),
    'ALTER TABLE store_container ADD CONSTRAINT uk_store_location_type_department_courier UNIQUE (location_id, store_type, department_id, courier_id)',
    'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── asset_type_custom_attribute: uk_asset_type_custom_attribute_key ───────────────────────────
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'asset_type_custom_attribute'
      AND INDEX_NAME = 'uk_asset_type_custom_attribute_key'),
    'ALTER TABLE asset_type_custom_attribute ADD CONSTRAINT uk_asset_type_custom_attribute_key UNIQUE (asset_type_id, attribute_key)',
    'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── store (StoreBalance): drop any legacy unique index on (commodity_id, branch_id) ───────────
-- Legacy indexes have auto-generated names, so discover by shape then drop.
SET @legacy := (
    SELECT INDEX_NAME FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'store' AND NON_UNIQUE = 0
    GROUP BY INDEX_NAME
    HAVING COUNT(*) = 2
       AND SUM(COLUMN_NAME = 'commodity_id') = 1
       AND SUM(COLUMN_NAME = 'branch_id') = 1
    LIMIT 1);
SET @ddl := IF(@legacy IS NOT NULL, CONCAT('ALTER TABLE store DROP INDEX ', @legacy), 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── store (StoreBalance): ensure uk_store_balance_commodity_store on (commodity_id, store_id) ──
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'store' AND NON_UNIQUE = 0
    GROUP BY INDEX_NAME
    HAVING COUNT(*) = 2
       AND SUM(COLUMN_NAME = 'commodity_id') = 1
       AND SUM(COLUMN_NAME = 'store_id') = 1),
    'CREATE UNIQUE INDEX uk_store_balance_commodity_store ON store (commodity_id, store_id)',
    'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
