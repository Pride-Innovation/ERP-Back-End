-- Money becomes DECIMAL(19,4) everywhere it is stored.
--
-- Two problems with what was there before:
--
--   * Prices were `int`. That caps a single value near 2,147,483,647 — around 2.1bn UGX, which a
--     high-value asset or a large order can genuinely exceed — and it cannot hold a fraction at all.
--   * `asset.purchase_cost` and `asset.cost_of_the_asset` were VARCHAR. No arithmetic was possible
--     without parsing, and nothing stopped a non-numeric value being saved.
--
-- Both make depreciation impossible to compute honestly: a rate applied to a cost and then divided
-- by twelve produces fractions on almost every line (5,000,000 x 33.33% / 12 = 138,875, and the
-- second year gives 92,587.9625). Four decimal places hold that exactly rather than rounding money
-- away on every period.
--
-- The _aud tables are converted alongside their entities: Stock and Asset are @Audited, and Envers
-- writes the same values into the audit row, so a type mismatch there would fail the insert and roll
-- back the whole transaction — no stock created, no asset registered.
--
-- MODIFY COLUMN is idempotent in effect (re-running sets the same type), so this is safe to re-run.

-- ── stock ───────────────────────────────────────────────────────────────────────────────────────
ALTER TABLE stock      MODIFY COLUMN total_cost   DECIMAL(19,4) NULL;
ALTER TABLE stock      MODIFY COLUMN balance_cost DECIMAL(19,4) NULL;
ALTER TABLE stock_aud  MODIFY COLUMN total_cost   DECIMAL(19,4) NULL;
ALTER TABLE stock_aud  MODIFY COLUMN balance_cost DECIMAL(19,4) NULL;

-- ── stock_commodity ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE stock_commodity MODIFY COLUMN cost_price     DECIMAL(19,4) NULL;
ALTER TABLE stock_commodity MODIFY COLUMN purchase_price DECIMAL(19,4) NULL;

-- ── grn_commodity ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE grn_commodity MODIFY COLUMN cost_price           DECIMAL(19,4) NULL;
ALTER TABLE grn_commodity MODIFY COLUMN purchase_price       DECIMAL(19,4) NULL;
ALTER TABLE grn_commodity MODIFY COLUMN total_cost_price     DECIMAL(19,4) NULL;
ALTER TABLE grn_commodity MODIFY COLUMN total_purchase_price DECIMAL(19,4) NULL;

-- ── asset: VARCHAR -> DECIMAL ───────────────────────────────────────────────────────────────────
-- Blank out anything that is not a plain number FIRST. A direct MODIFY on a varchar column silently
-- coerces "abc" to 0 under a non-strict sql_mode, which would invent a cost of zero for the asset
-- rather than admitting the value was never usable. NULL is the honest outcome.
UPDATE asset SET purchase_cost = NULL
WHERE purchase_cost IS NOT NULL
  AND (TRIM(purchase_cost) = '' OR REPLACE(REPLACE(TRIM(purchase_cost), ',', ''), ' ', '') NOT REGEXP '^[0-9]+(\\.[0-9]+)?$');

UPDATE asset SET cost_of_the_asset = NULL
WHERE cost_of_the_asset IS NOT NULL
  AND (TRIM(cost_of_the_asset) = '' OR REPLACE(REPLACE(TRIM(cost_of_the_asset), ',', ''), ' ', '') NOT REGEXP '^[0-9]+(\\.[0-9]+)?$');

-- Strip grouping separators that were legal in the old free-text column.
UPDATE asset SET purchase_cost     = REPLACE(REPLACE(purchase_cost, ',', ''), ' ', '')     WHERE purchase_cost IS NOT NULL;
UPDATE asset SET cost_of_the_asset = REPLACE(REPLACE(cost_of_the_asset, ',', ''), ' ', '') WHERE cost_of_the_asset IS NOT NULL;

ALTER TABLE asset MODIFY COLUMN purchase_cost     DECIMAL(19,4) NULL;
ALTER TABLE asset MODIFY COLUMN cost_of_the_asset DECIMAL(19,4) NULL;

-- Same treatment for the audit table.
UPDATE asset_aud SET purchase_cost = NULL
WHERE purchase_cost IS NOT NULL
  AND (TRIM(purchase_cost) = '' OR REPLACE(REPLACE(TRIM(purchase_cost), ',', ''), ' ', '') NOT REGEXP '^[0-9]+(\\.[0-9]+)?$');

UPDATE asset_aud SET cost_of_the_asset = NULL
WHERE cost_of_the_asset IS NOT NULL
  AND (TRIM(cost_of_the_asset) = '' OR REPLACE(REPLACE(TRIM(cost_of_the_asset), ',', ''), ' ', '') NOT REGEXP '^[0-9]+(\\.[0-9]+)?$');

UPDATE asset_aud SET purchase_cost     = REPLACE(REPLACE(purchase_cost, ',', ''), ' ', '')     WHERE purchase_cost IS NOT NULL;
UPDATE asset_aud SET cost_of_the_asset = REPLACE(REPLACE(cost_of_the_asset, ',', ''), ' ', '') WHERE cost_of_the_asset IS NOT NULL;

ALTER TABLE asset_aud MODIFY COLUMN purchase_cost     DECIMAL(19,4) NULL;
ALTER TABLE asset_aud MODIFY COLUMN cost_of_the_asset DECIMAL(19,4) NULL;
