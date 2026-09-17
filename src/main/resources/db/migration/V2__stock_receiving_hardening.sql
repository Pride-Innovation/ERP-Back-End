-- Stock receiving hardening: business dates, PO number, close-short reason, idempotency key,
-- and the unique indexes that ddl-auto=update did not reliably create.
--
-- This script is idempotent/guarded: the columns may already exist if the app was previously
-- started with ddl-auto=update against this database, so each change is applied only when missing
-- (MySQL 8 has no "ADD COLUMN IF NOT EXISTS"). Guards use information_schema + prepared statements.

SET @schema := DATABASE();

-- ── stock: new columns ──────────────────────────────────────────────────────────────────────
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock' AND COLUMN_NAME = 'po_number'),
    'ALTER TABLE stock ADD COLUMN po_number VARCHAR(255) NULL', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock' AND COLUMN_NAME = 'order_date'),
    'ALTER TABLE stock ADD COLUMN order_date DATETIME(6) NULL', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock' AND COLUMN_NAME = 'delivery_date'),
    'ALTER TABLE stock ADD COLUMN delivery_date DATETIME(6) NULL', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock' AND COLUMN_NAME = 'invoice_date'),
    'ALTER TABLE stock ADD COLUMN invoice_date DATETIME(6) NULL', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock' AND COLUMN_NAME = 'close_short_reason'),
    'ALTER TABLE stock ADD COLUMN close_short_reason VARCHAR(255) NULL', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock' AND COLUMN_NAME = 'idempotency_key'),
    'ALTER TABLE stock ADD COLUMN idempotency_key VARCHAR(255) NULL', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── stock: unique index on idempotency_key (multiple NULLs are allowed by MySQL) ─────────────
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock' AND INDEX_NAME = 'uk_stock_idempotency_key'),
    'ALTER TABLE stock ADD CONSTRAINT uk_stock_idempotency_key UNIQUE (idempotency_key)', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── grn_report: unique index on the GRN number (name) ────────────────────────────────────────
-- NOTE: fails if pre-existing rows have duplicate names. Legacy "GRN-<millis>" values are unique,
-- so this should apply cleanly; if it errors, de-duplicate grn_report.name first.
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'grn_report' AND INDEX_NAME = 'uk_grn_report_name'),
    'ALTER TABLE grn_report ADD CONSTRAINT uk_grn_report_name UNIQUE (name)', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
