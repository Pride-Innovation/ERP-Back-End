-- Receipt-based goods receiving.
--
-- A purchase order is usually delivered in batches. Until now a batch had nowhere to live: the
-- delivery date sat on the mutable stock header (so a later batch silently rewrote the depreciation
-- start date of an earlier one), each commodity received on its own produced a separate GRN, and the
-- supplier's delivery-note reference was not captured at all. `stock_receipt` gives one physical
-- delivery one row, and `grn_report.stock_receipt_id` makes the GRN that batch's document.
--
-- Also adds the `version` columns backing optimistic locking on the two tables that receiving does a
-- read-modify-write against, so two storekeepers receiving the same order at once can no longer
-- overwrite each other's batch.
--
-- Guarded with information_schema checks (MySQL/MariaDB have no "ADD COLUMN IF NOT EXISTS"), so the
-- script is safe to re-run and safe on a database that ddl-auto=update already touched.

SET @schema := DATABASE();

-- ── stock_receipt: one row per physical delivery ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stock_receipt (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    stock_id             BIGINT       NOT NULL,
    receipt_date         DATETIME(6)  NOT NULL,
    delivery_note_number VARCHAR(255) NULL,
    invoice_number       VARCHAR(255) NULL,
    notes                VARCHAR(1000) NULL,
    store_id             BIGINT       NULL,
    idempotency_key      VARCHAR(255) NULL,
    disposed             BIT(1)       NULL DEFAULT b'0',
    disposal_date        DATETIME(6)  NULL,
    create_date          DATETIME(6)  NULL,
    last_modified        DATETIME(6)  NULL,
    created_by           BIGINT       NULL,
    last_modified_by     BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_receipt_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_stock_receipt_stock FOREIGN KEY (stock_id) REFERENCES stock (id),
    CONSTRAINT fk_stock_receipt_store FOREIGN KEY (store_id) REFERENCES store_container (id),
    INDEX idx_stock_receipt_stock (stock_id)
) ENGINE = InnoDB;

-- ── grn_report → the receipt it documents ───────────────────────────────────────────────────────
-- Nullable: GRNs issued before receipts existed have no batch to point at. @NotAudited on the
-- entity field, so grn_report_aud deliberately gets no matching column.
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'grn_report' AND COLUMN_NAME = 'stock_receipt_id'),
    'ALTER TABLE grn_report ADD COLUMN stock_receipt_id BIGINT NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'grn_report' AND INDEX_NAME = 'idx_grn_report_stock_receipt'),
    'CREATE INDEX idx_grn_report_stock_receipt ON grn_report (stock_receipt_id)', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'grn_report' AND CONSTRAINT_NAME = 'fk_grn_report_stock_receipt'),
    'ALTER TABLE grn_report ADD CONSTRAINT fk_grn_report_stock_receipt FOREIGN KEY (stock_receipt_id) REFERENCES stock_receipt (id)',
    'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── optimistic locking counters ─────────────────────────────────────────────────────────────────
-- NOT NULL DEFAULT 0 so pre-existing rows start at a valid version; a NULL version makes Hibernate
-- treat a loaded row as transient and the first save would fail.
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'stock_commodity' AND COLUMN_NAME = 'version'),
    'ALTER TABLE stock_commodity ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- `store` is the StoreBalance table (the container lives in `store_container`).
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'store' AND COLUMN_NAME = 'version'),
    'ALTER TABLE store ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
