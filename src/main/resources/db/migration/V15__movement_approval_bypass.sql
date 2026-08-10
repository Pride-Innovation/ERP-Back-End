-- Records a movement that proceeded without approval because no approver could be resolved.
--
-- The approval trail already carries who confirmed it and why (a BYPASSED tier); this flag is the
-- denormalized copy so a listing can badge the exception without reading the trail per row. An
-- unapproved movement must never look identical to an approved one.
--
-- `movement` is @Audited, so the Envers mirror needs the column too. Without it every write to a
-- movement fails at runtime with "Unknown column ... in 'field list'" and rolls back the whole
-- surrounding transaction — the failure that silently killed stocking when `stock_aud` fell behind.

ALTER TABLE movement     ADD COLUMN approval_bypassed BIT NOT NULL DEFAULT 0;
ALTER TABLE movement_aud ADD COLUMN approval_bypassed BIT NULL;
