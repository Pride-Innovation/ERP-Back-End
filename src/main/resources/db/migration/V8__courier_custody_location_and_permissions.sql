-- Courier custody stores stop pretending to be at Head Office.
--
-- `store_container.location_id` was NOT NULL, so a courier's custody store had to be given a branch
-- to satisfy the column and was pinned to Head Office. The consequence was that every unit and every
-- asset in transit anywhere in the country reported as Head Office stock: balance queries had to be
-- littered with defensive COURIER filters to work around it, and `asset.branch` was simply wrong for
-- the whole time an item was on the road.
--
-- A courier's custody store is not at a place. Its identity is its courier, and whatever it holds is
-- between two locations rather than at one — so the honest value is NULL.
--
-- `store.branch_id` (the StoreBalance table) is relaxed for the same reason: a custody balance has no
-- branch. Every branch-scoped query already excludes COURIER stores, so a NULL is never surfaced as
-- branch stock, and the one place that reads the column (StoreSeeder's legacy backfill) already
-- skips rows without a branch.
--
-- Guarded so the script is safe to re-run.

SET @schema := DATABASE();

-- ── store_container.location_id becomes nullable ────────────────────────────────────────────────
SET @ddl := (SELECT IF(EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'store_container'
      AND COLUMN_NAME = 'location_id' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE store_container MODIFY COLUMN location_id BIGINT NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── store.branch_id (StoreBalance) becomes nullable ─────────────────────────────────────────────
SET @ddl := (SELECT IF(EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = 'store'
      AND COLUMN_NAME = 'branch_id' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE store MODIFY COLUMN branch_id BIGINT NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── Detach existing courier custody stores from their borrowed location ─────────────────────────
UPDATE store_container SET location_id = NULL WHERE store_type = 'COURIER';

UPDATE store b
    JOIN store_container sc ON sc.id = b.store_id
SET b.branch_id = NULL
WHERE sc.store_type = 'COURIER';

-- ── VIEW_ALL_BRANCHES is created in Java, not here ──────────────────────────────────────────────
-- DataInitializer.ensurePermission() owns permission creation (ids come from a sequence table, not
-- AUTO_INCREMENT), and the super-admin role receives every permission on startup.
--
-- It is deliberately NOT back-granted to existing roles. Absence of the permission is what confines
-- a user to their own branch, so auto-granting it to everyone who can currently read a store would
-- leave every branch user seeing the whole organisation — precisely the behaviour this change
-- exists to stop. Grant it explicitly to the Head Office staff who need a national view.
