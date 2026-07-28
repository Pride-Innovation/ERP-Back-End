-- Stock take: counting a store's consumables and reconciling the balances to the shelf.
--
-- Three new tables, nothing existing altered:
--
--   stock_count       one physical count of a store
--   stock_count_line  one commodity on that count sheet
--   stock_adjustment  the immutable, reason-coded record of a balance being corrected
--
-- `stock_adjustment` is the point of the exercise. Receipts and issues already have documents behind
-- them (a GRN, an issuance record), so you can always ask why they happened; an adjustment had
-- nothing — the balance simply became a different number. Every posted variance now leaves a row
-- here saying what changed, by how much, why, who approved it and when. It is also the first slice
-- of the wider stock ledger, and deliberately the slice where the reason matters most.
--
-- None of these are Envers-audited: stock_adjustment is append-only by design (a wrong adjustment is
-- corrected by counting again and posting another), so a revision history of it would be noise.

CREATE TABLE IF NOT EXISTS stock_count (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    reference        VARCHAR(255) NULL,
    store_id         BIGINT       NOT NULL,
    asset_type_id    BIGINT       NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    blind            BIT(1)       NOT NULL DEFAULT b'1',
    counted_by_id    BIGINT       NULL,
    approved_by_id   BIGINT       NULL,
    submitted_at     DATETIME(6)  NULL,
    approved_at      DATETIME(6)  NULL,
    posted_at        DATETIME(6)  NULL,
    notes            VARCHAR(1000) NULL,
    review_remarks   VARCHAR(1000) NULL,
    create_date      DATETIME(6)  NULL,
    last_modified    DATETIME(6)  NULL,
    created_by       BIGINT       NULL,
    last_modified_by BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_count_reference UNIQUE (reference),
    CONSTRAINT fk_stock_count_store FOREIGN KEY (store_id) REFERENCES store_container (id),
    CONSTRAINT fk_stock_count_asset_type FOREIGN KEY (asset_type_id) REFERENCES asset_type (id),
    CONSTRAINT fk_stock_count_counted_by FOREIGN KEY (counted_by_id) REFERENCES users (id),
    CONSTRAINT fk_stock_count_approved_by FOREIGN KEY (approved_by_id) REFERENCES users (id),
    INDEX idx_stock_count_store_status (store_id, status)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS stock_count_line (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    stock_count_id           BIGINT      NOT NULL,
    commodity_id             BIGINT      NOT NULL,
    -- What the system believed when the sheet was opened. Variances are measured against this
    -- rather than against a live figure that may have moved while the shelves were being counted.
    system_quantity_at_start INT         NOT NULL DEFAULT 0,
    -- NULL means "not counted yet", which is deliberately different from a counted zero: treating a
    -- blank as zero would write off stock nobody actually looked for.
    counted_quantity         INT         NULL,
    reason                   VARCHAR(30) NULL,
    note                     VARCHAR(500) NULL,
    recount_required         BIT(1)      NOT NULL DEFAULT b'0',
    counted_at               DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_count_line_commodity UNIQUE (stock_count_id, commodity_id),
    CONSTRAINT fk_stock_count_line_count FOREIGN KEY (stock_count_id) REFERENCES stock_count (id),
    CONSTRAINT fk_stock_count_line_commodity FOREIGN KEY (commodity_id) REFERENCES commodity (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS stock_adjustment (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    store_id         BIGINT      NOT NULL,
    commodity_id     BIGINT      NOT NULL,
    quantity_delta   INT         NOT NULL,
    quantity_before  INT         NOT NULL,
    quantity_after   INT         NOT NULL,
    reason           VARCHAR(30) NOT NULL,
    note             VARCHAR(500) NULL,
    stock_count_id   BIGINT      NULL,
    approved_by      BIGINT      NULL,
    posted_at        DATETIME(6) NOT NULL,
    posted_by        BIGINT      NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_adjustment_store FOREIGN KEY (store_id) REFERENCES store_container (id),
    CONSTRAINT fk_stock_adjustment_commodity FOREIGN KEY (commodity_id) REFERENCES commodity (id),
    CONSTRAINT fk_stock_adjustment_count FOREIGN KEY (stock_count_id) REFERENCES stock_count (id),
    INDEX idx_stock_adjustment_store_posted (store_id, posted_at),
    INDEX idx_stock_adjustment_reason (reason)
) ENGINE = InnoDB;
