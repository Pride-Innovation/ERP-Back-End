-- Which columns each exportable table puts into its PDF / Excel.
--
-- Purely presentational: every column here is already on screen and stays on screen, and the file is
-- still built in the browser from rows it already holds. This decides what goes *into the file*, so
-- a register that runs to eighteen columns can be printed as the six anyone actually reads. It is
-- not an access control and must never be mistaken for one — anybody who can open the table can read
-- every column of it regardless of what is configured here.
--
-- ── Why this is a table at all, when the feature is "frontend" ──────────────
--
-- Because somebody sets it *for everyone*. A per-viewer choice needs no server — browser storage
-- would do — but then the person with the permission would tick columns and nothing would change for
-- anyone else, and the permission would be decorative. A shared default needs somewhere shared.
--
-- Individual users still narrow their own download on top of this; that half genuinely is local and
-- stays in the browser.

CREATE TABLE export_column_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- The stable identity of a table, from the frontend registry (`exportTables.ts`).
    --
    -- Deliberately not the `module` string those tables already pass around. That string cannot
    -- identify a table: the assets page passes the *asset category name*, so one table arrives under
    -- twelve different names, and both the asset-request and the fleet-requisition tabs call
    -- themselves "request", so two unrelated tables arrive under one. A config keyed on it would
    -- fragment one table and merge two others.
    table_key VARCHAR(100) NOT NULL,
    -- The row field this column reads, e.g. `engravedNumber`. Matches the registry's column key.
    column_key VARCHAR(100) NOT NULL,
    -- Position in the exported file, so the configured set has a deliberate reading order rather
    -- than whatever order the row object happened to be built in.
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- One row per column per table. Saving is a replacement, so this also stops a half-failed write
    -- leaving a column listed twice.
    CONSTRAINT uq_export_column UNIQUE (table_key, column_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_export_column_table ON export_column_config (table_key, sort_order);

-- ── The fail-open this schema has to express, and why it is not a bug ───────
--
-- A table with NO rows here exports EVERY column it has. That is the widest answer, and it is the
-- correct default: this ships against twenty-odd existing tables that nobody has configured yet, and
-- the alternative is that the day it deploys every export in the bank comes out blank.
--
-- Which means "no rows" and "configured to nothing" must NOT be the same state. They are not: the
-- service treats an absent table_key as unconfigured, and a table configured down to a single column
-- still has that one row. There is deliberately no way to save an empty set — the editor requires at
-- least one column — because a table that exports nothing is a broken export, not a preference.
--
-- This is the same shape as the "allowed ids" trap recorded in CLAUDE.md, where an empty list means
-- *no restriction* rather than *nothing allowed*. Here the widest answer is the one we want; the
-- point is that it is chosen and written down, not stumbled into.
