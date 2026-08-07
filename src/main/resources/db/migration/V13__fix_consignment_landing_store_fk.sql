-- Repoint consignment.landing_store_id at the table stores actually live in.
--
-- V12 declared `REFERENCES store (id)`. The Store entity maps to `store_container`
-- (@Table(name = "store_container")), not `store`, so the constraint was pointing at a different
-- table entirely. Everything worked until a consignment arrived: the ledger applied correctly —
-- assets and stock balances carry their own, correct foreign keys — and then the UPDATE that stamps
-- landing_store_id onto the consignment failed with errno 1452, rolling the whole arrival back.
--
-- The mistake survived the migration because a table named `store` does exist in this schema; had it
-- not, V12 would have failed outright and the error would have surfaced months earlier than an
-- operator clicking "Arrived".
--
-- No data repair is needed: every arrival attempt rolled back, so no row ever carried a value here.

ALTER TABLE consignment DROP FOREIGN KEY fk_consignment_landing_store;

ALTER TABLE consignment ADD CONSTRAINT fk_consignment_landing_store
    FOREIGN KEY (landing_store_id) REFERENCES store_container (id);
