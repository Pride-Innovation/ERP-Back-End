-- Movement custody and returns.
--
-- Three changes, all additive:
--
-- 1. `source_user_id` + a nullable `source_store_id`. A movement can now originate from a person
--    handing an item back (RETURN_TO_STORE), not only from a store. Exactly one of the two is set,
--    enforced in MovementService. Without this there was no way to model "the user returns it", so a
--    branch Admin store was never credited when stock came back.
--
-- 2. `origin_holder_id` + `origin_location_id`. Captured on a REPAIR_TRANSFER at initiation, from
--    the asset's own assignment. The assignment is closed when the branch admin takes custody, so
--    without recording it first the system forgets who the asset belongs to and the return leg has
--    to be aimed by hand — which is how a repaired asset ends up with the wrong person.
--
-- No matching movement_aud columns: every foreign key on Movement is @NotAudited, so Envers does
-- not track them and adding columns there would be dead weight.
--
-- Guarded with information_schema checks (MySQL/MariaDB have no "ADD COLUMN IF NOT EXISTS"), so the
-- script is safe to re-run.

SET @schema := DATABASE();

-- ── movement.source_user_id ─────────────────────────────────────────────────────────────────────
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'movement' AND COLUMN_NAME = 'source_user_id'),
    'ALTER TABLE movement ADD COLUMN source_user_id BIGINT NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'movement' AND CONSTRAINT_NAME = 'fk_movement_source_user'),
    'ALTER TABLE movement ADD CONSTRAINT fk_movement_source_user FOREIGN KEY (source_user_id) REFERENCES users (id)',
    'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── movement.source_store_id becomes nullable ───────────────────────────────────────────────────
-- A return has no source store. Existing rows all have one, so nothing is lost by relaxing this.
SET @ddl := (SELECT IF(EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'movement'
      AND COLUMN_NAME = 'source_store_id' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE movement MODIFY COLUMN source_store_id BIGINT NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── movement.origin_holder_id / origin_location_id ──────────────────────────────────────────────
SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'movement' AND COLUMN_NAME = 'origin_holder_id'),
    'ALTER TABLE movement ADD COLUMN origin_holder_id BIGINT NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'movement' AND COLUMN_NAME = 'origin_location_id'),
    'ALTER TABLE movement ADD COLUMN origin_location_id BIGINT NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'movement' AND CONSTRAINT_NAME = 'fk_movement_origin_holder'),
    'ALTER TABLE movement ADD CONSTRAINT fk_movement_origin_holder FOREIGN KEY (origin_holder_id) REFERENCES users (id)',
    'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(NOT EXISTS(
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'movement' AND CONSTRAINT_NAME = 'fk_movement_origin_location'),
    'ALTER TABLE movement ADD CONSTRAINT fk_movement_origin_location FOREIGN KEY (origin_location_id) REFERENCES branch (id)',
    'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- Repair transfers already in flight have no recorded origin. They are deliberately left alone:
-- RepairMovementService falls back to the asset's own assignedTo, which is exactly how the return
-- leg behaved before this change, so nothing in flight breaks and no backfill is needed.
