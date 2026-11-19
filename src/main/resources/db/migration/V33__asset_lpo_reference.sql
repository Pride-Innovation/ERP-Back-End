-- An LPO number recorded as plain text, for assets that came in through the bulk import.
--
-- A received asset's LPO is its stock order's (`stock.lpo_number`, reached through `asset.stock_id`).
-- An imported asset has no stock order - many are old records whose order was never in this system -
-- so it had nowhere to keep an LPO even when one was known. This is that place: optional, a reference
-- only, never a link to an order. Nothing looks a stock order up by it.
--
-- Asset is @Audited, so asset_aud needs the column too (trap #5).

ALTER TABLE asset     ADD COLUMN lpo_reference VARCHAR(100) NULL;
ALTER TABLE asset_aud ADD COLUMN lpo_reference VARCHAR(100) NULL;
