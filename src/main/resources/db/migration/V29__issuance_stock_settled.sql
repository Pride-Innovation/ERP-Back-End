-- Issued stock leaves the store when the issuance is approved, not when it is issued.
--
-- ── Why ─────────────────────────────────────────────────────────────────────
--
-- A same-location issuance used to debit the issuer's Admin store the moment items were issued,
-- before anyone had approved the issuance. Rejecting it afterwards put nothing back - no code path
-- credited it - so the store read lower than the shelf, and the next stock take reported the
-- difference as lost. The debit now happens at approval, and a rejected issuance touches no stock.
--
-- ── The marker ──────────────────────────────────────────────────────────────
--
-- `stock_settled` records that this issuance's stock is accounted for: debited at approval for a
-- direct issuance, or handed to the fulfilment movement for a cross-location one. Every issuance
-- that exists today was settled under the old rule (debited at issue, or carried by a movement),
-- so all of them start TRUE. Without that, approving one issued before this change would debit it
-- a second time. Measured when written: no issuance was between issue and approval.
--
-- Issuance is @Audited, so issuance_aud needs the column too or every write fails (trap #5).

ALTER TABLE issuance     ADD COLUMN stock_settled BIT(1) NOT NULL DEFAULT b'0';
ALTER TABLE issuance_aud ADD COLUMN stock_settled BIT(1) NULL;

UPDATE issuance SET stock_settled = b'1';
