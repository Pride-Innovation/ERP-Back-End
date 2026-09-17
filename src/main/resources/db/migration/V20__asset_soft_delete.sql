-- Soft delete for assets.
--
-- Deleting an asset record removes it from every listing without removing it from the database. The
-- row survives, so its assignment history and repair history still point at something real and the
-- audit trail can still be read back — which is the whole reason for doing it this way rather than
-- with a DELETE.
--
-- Distinct from disposal, which is a business event: an asset reaching the end of its life is
-- written off through a movement, keeps its place in the register, and is counted as disposed. This
-- flag says something narrower and more administrative — "this record should not have existed", a
-- duplicate or a mis-keyed import. The two are separate columns because they answer separate
-- questions, and an asset can legitimately be both.
--
-- Follows the pattern already set by `request.deleted`.

ALTER TABLE asset
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Envers.
--
-- `asset` is @Audited, and a new column on an audited entity needs the matching column on its _aud
-- table or EVERY write to the entity fails at runtime — not just deletes. Adding one and forgetting
-- the other takes the whole asset module down on deploy, which is trap #5 in CLAUDE.md.
--
-- Nullable here, unlike on the entity table: existing revisions genuinely have no value for a column
-- that did not exist when they were written, and NOT NULL would force a false answer onto history.
ALTER TABLE asset_aud
    ADD COLUMN deleted BOOLEAN NULL;

-- Indexed because every asset listing now filters on it, and the register is the largest table the
-- application reads. Composite with `id` so the index also serves the pagination tie-break rather
-- than being consulted and then discarded.
CREATE INDEX idx_asset_deleted ON asset (deleted, id);
