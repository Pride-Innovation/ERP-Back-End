-- A request can be for restocking the branch store rather than for the requester (Phase 2,
-- WORKFLOW.md).
--
-- ── Why ─────────────────────────────────────────────────────────────────────
--
-- The system used to guess: a request raised by the branch's designated Operations Manager was a
-- "replenishment", anything else personal. The guess was wrong both ways - a BOM's own laptop was
-- labelled a restock, a BM could never restock - and either way the goods were delivered to the
-- person, so consumables counted as used on arrival and the branch store was never credited.
--
-- `for_store_restock` is now chosen on the request (a checkbox, guarded by RESTOCK_STORE, branch
-- users only) and decides where the goods go: into the requester's branch Admin store. A request is
-- one or the other in full; store and personal items are separate requests.
--
-- `applies_to` lets a workflow say which kind it serves - PERSONAL, RESTOCK or BOTH - so a branch
-- workflow that issues from the branch store can serve personal requests while restock requests go
-- to Head Office. Every existing workflow starts as BOTH, so nothing changes until an administrator
-- configures it.
--
-- Request is @Audited, so request_aud needs the column too (trap #5). ApprovalWorkflow is not audited.

ALTER TABLE request     ADD COLUMN for_store_restock BIT(1) NOT NULL DEFAULT b'0';
ALTER TABLE request_aud ADD COLUMN for_store_restock BIT(1) NULL;

ALTER TABLE approval_workflow ADD COLUMN applies_to VARCHAR(16) NOT NULL DEFAULT 'BOTH';
