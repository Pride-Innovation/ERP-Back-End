-- The asset status a movement must put back when it completes.
--
-- A repair transfer stamps the asset "In Maintenance" and records here what it was before. The
-- matching return copies that across and applies it on completion, so a repaired laptop goes back to
-- its owner reading whatever it read before the fault — not still In Maintenance, which is what
-- happened while nothing on the return path cleared the flag.
--
-- Nullable: every movement created before this existed has no snapshot, and no other movement type
-- records one. The return falls back to a sensible status when it finds none.
--
-- `movement` is @Audited, so the Envers mirror needs the column too — without it every movement
-- write fails at runtime with "Unknown column ... in 'field list'" and rolls back its whole
-- transaction.

ALTER TABLE movement     ADD COLUMN restore_status_code VARCHAR(60) NULL;
ALTER TABLE movement_aud ADD COLUMN restore_status_code VARCHAR(60) NULL;
