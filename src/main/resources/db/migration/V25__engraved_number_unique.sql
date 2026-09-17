-- An engraved number identifies one physical asset, so the database should say so.
--
-- ── Why this was not already true ───────────────────────────────────────────
--
-- It nearly was, on the wrong column. `Asset.tagName` carries `@Column(unique = true)` and Hibernate
-- duly created `UK_40ixkldrr3u33nslfjxve2bs` on `tag_name` — but that field is **NULL on all 252
-- rows** and nothing in the application writes it. The estate identifies assets by
-- `engraved_number`, which had no constraint at all. Somebody built this correctly once, against a
-- field that was then replaced rather than removed.
--
-- The consequence was live, not theoretical. The navigation bar's tag search calls
-- `findByEngravedNumber`, which returns `Optional<Asset>` — so a duplicate does not return the first
-- match, it throws `IncorrectResultSizeDataAccessException`. Measured before this migration:
--
--     'PBL-2026-LPT07' -> Query did not return a unique result: 2 results were returned
--
-- Three engraved numbers were carried by two assets each, so searching for any of those six assets
-- produced a 500.
--
-- ── The de-duplication, and why it is written generically ───────────────────
--
-- The three collisions in the environment this was written against are:
--
--     PBL-2026-LPT06    assets 155 (serial 123RTMAC93032) and 307 (no serial)
--     PBL-2026-LPT07    assets 154 (serial 123RTMAC93033) and 306 (no serial)
--     PBL-CTHNG-FNT001  assets 1310 and 1315
--
-- Those ids are **not** written into this migration. Another environment will have different
-- collisions — or none — and a migration that named these would silently do nothing there while the
-- `ALTER TABLE` below failed on rows it had not touched. So the rule is stated instead: within each
-- group sharing an engraved number, the **lowest id keeps it** and every other row has its own id
-- appended.
--
-- Lowest-id-keeps is arbitrary but deterministic, which is what matters for a repair: re-running the
-- reasoning gives the same answer. It also happens to keep the better record in both laptop cases
-- here, where the lower id is the one carrying a serial number.
--
-- **Re-tagging rather than deleting or merging**, deliberately. All six rows are live assets that
-- somebody currently holds — two are assigned and two issued — and each is a distinct physical item:
-- two laptops, two coat hangers. Soft-deleting one of a pair would remove a real asset from the
-- register and orphan its assignment history; merging would fold two custody trails into one. Both
-- destroy information to fix a label. A suffixed number is visibly a repair, keeps both records and
-- both trails, and is corrected by re-tagging the item in Settings whenever somebody reads the
-- physical label.

UPDATE asset a
JOIN (
    SELECT id
      FROM asset
     WHERE engraved_number IS NOT NULL
       AND TRIM(engraved_number) <> ''
       AND id NOT IN (
           SELECT keeper FROM (
               SELECT MIN(id) AS keeper
                 FROM asset
                WHERE engraved_number IS NOT NULL
                  AND TRIM(engraved_number) <> ''
                GROUP BY engraved_number
           ) AS keepers
       )
) AS losers ON losers.id = a.id
   SET a.engraved_number = CONCAT(a.engraved_number, '-', a.id);

-- Grouping on the bare column is deliberate: `engraved_number` is `utf8mb4_0900_ai_ci`, so MySQL
-- already compares it case- and accent-insensitively. "PBL/IT/0412" and "pbl/it/0412" group together
-- here and collide under the constraint below — which is the behaviour wanted, and the same rule the
-- bulk import has always applied with `existsByEngravedNumberIgnoreCase`. Writing LOWER() would have
-- looked more careful and changed nothing.

-- ── The constraint ─────────────────────────────────────────────────────────
--
-- NULL stays allowed, and that is load-bearing rather than an oversight: MySQL treats NULLs as
-- distinct in a unique index, and **33 assets currently have no engraved number**. All 33 are Head
-- Office rows at status `requireUpdate` — "not yet completed after stocking", in the issuance
-- service's own words — created by the stocking flow precisely so the number can be filled in when
-- somebody reads the label off the item. Requiring it here would break receiving stock.
--
-- The service keeps its own check as well. This index is what makes uniqueness true under two
-- concurrent writes; the service check is what produces a message naming the asset already holding
-- the number. Same division as `staff_number` in V23, and for the same reason: a check without a
-- constraint is advice, and a constraint without a check is a stack trace.

ALTER TABLE asset
    ADD CONSTRAINT uk_asset_engraved_number UNIQUE (engraved_number);

-- `tag_name` and its unique index are left exactly as they are. Dropping an audited column means
-- handling `asset_aud` too (trap #5), and a dead column with a correct constraint harms nothing
-- where it sits. It is recorded here so that whoever next wonders why the search endpoint is called
-- `/assets/tag-name` while searching `engraved_number` finds the answer rather than rediscovering it.
