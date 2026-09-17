-- The stock ledger: an immutable record of every quantity entering or leaving a store.
--
-- `store.quantity` (StoreBalance) was a bare running total. You could see that a store held twelve
-- of something, but not how it reached twelve, and if the figure was wrong there was no way to work
-- out where it went wrong or to rebuild it. Envers recorded WHO changed a balance and WHEN, but
-- never WHY or AGAINST WHICH DOCUMENT — which is the question anyone actually asks.
--
-- Every credit and debit through InventoryService now writes a row here, carrying a reason and a
-- pointer back to the document that caused it. The balance becomes a cache of these rows rather than
-- the only copy of the truth, and /inventory/ledger/verify recomputes it to report drift.
--
-- Append-only by design: a wrong entry is corrected by posting an opposing one, never by editing
-- history. That is also why there is no _aud table — the ledger IS the history.
--
-- NOTE ON EXISTING DATA: balances that predate this table have no entries behind them, so the first
-- verify() run will report them as drift. That is accurate rather than a fault — it is precisely the
-- historical position the ledger cannot account for. No backfill is attempted: inventing entries to
-- make the numbers agree would be fabricating an audit trail.

CREATE TABLE IF NOT EXISTS stock_ledger_entry (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    store_id       BIGINT       NOT NULL,
    commodity_id   BIGINT       NOT NULL,
    -- Signed: positive brought stock in, negative took it out. Never zero.
    quantity_delta INT          NOT NULL,
    -- The balance after this entry. Redundant against replaying history, deliberately: it makes a
    -- single row self-explanatory and lets drift be spotted between consecutive entries.
    balance_after  INT          NOT NULL,
    reason         VARCHAR(30)  NOT NULL,
    source_type    VARCHAR(40)  NULL,
    source_id      BIGINT       NULL,
    note           VARCHAR(500) NULL,
    occurred_at    DATETIME(6)  NOT NULL,
    actor_id       BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ledger_store     FOREIGN KEY (store_id)     REFERENCES store_container (id),
    CONSTRAINT fk_ledger_commodity FOREIGN KEY (commodity_id) REFERENCES commodity (id),
    INDEX idx_ledger_store_commodity (store_id, commodity_id),
    INDEX idx_ledger_occurred (occurred_at),
    INDEX idx_ledger_source (source_type, source_id)
) ENGINE = InnoDB;
