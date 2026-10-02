-- Who worked on a repair, as a reference to a person or a vendor rather than as typed text.
--
-- ── Why ─────────────────────────────────────────────────────────────────────
--
-- `repair.technician` was a free-text box. Nothing tied it to anybody, so the same Infra officer
-- could appear under three spellings, a consultant's name could be typed for an internal job, and
-- no report could ever answer "what has this person or vendor repaired".
--
-- A technician is now one of two things, chosen in that order on every form:
--
--   INTERNAL  a member of the unit that services the repair's routing - the Infra unit for IT
--             repairs, the Admin unit for Admin repairs - recorded as `technician_user_id`
--   EXTERNAL  a repair consultant registered in Settings, recorded as `technician_consultant_id`
--
-- `technician` stays, as the name at the time it was recorded. Old rows keep what was typed, and a
-- person renamed or a consultant edited later does not rewrite who did a past repair.
--
-- ── Why the routing is stored here too ──────────────────────────────────────
--
-- Which unit an internal technician must come from depends on where the repair went, and that was
-- only recorded on the movement - by store type, or by the presence of a consultant. Assigning a
-- technician days later from the asset's repair history would have had to rediscover it from the
-- journey every time. `repair_destination` keeps the answer on the record it governs.
--
-- `repair` is not audited, so there is no `_aud` table to extend (trap #5 does not apply).

ALTER TABLE repair
    ADD COLUMN repair_destination       VARCHAR(16) NULL,
    ADD COLUMN technician_type          VARCHAR(16) NULL,
    ADD COLUMN technician_user_id       BIGINT      NULL,
    ADD COLUMN technician_consultant_id BIGINT      NULL,
    ADD CONSTRAINT fk_repair_technician_user
        FOREIGN KEY (technician_user_id) REFERENCES users (id),
    ADD CONSTRAINT fk_repair_technician_consultant
        FOREIGN KEY (technician_consultant_id) REFERENCES consultant (id);

-- ── Backfill the routing from the journey that opened each record ───────────
--
-- A consultant on the transfer means EXTERNAL; otherwise the custody store says IT or ADMIN. A record
-- with no linked transfer keeps NULL and is resolved from its asset's category when it is read.

UPDATE repair r
JOIN movement m       ON m.repair_id = r.id AND m.movement_type = 'REPAIR_TRANSFER'
LEFT JOIN store_container s ON s.id = m.dest_store_id
   SET r.repair_destination = CASE
           WHEN m.consultant_id IS NOT NULL THEN 'EXTERNAL'
           WHEN s.store_type = 'IT'         THEN 'IT'
           ELSE 'ADMIN'
       END;

-- ── And the external technician, which was already known ────────────────────
--
-- An EXTERNAL repair's technician defaulted to the consultant's name, so wherever the text is blank
-- or still reads exactly that name, the reference is unambiguous. Anything else somebody typed is
-- left as typed: guessing a person from a string is how the field came to be untrustworthy.

UPDATE repair r
JOIN movement m   ON m.repair_id = r.id AND m.movement_type = 'REPAIR_TRANSFER'
JOIN consultant c ON c.id = m.consultant_id
   SET r.technician_type          = 'EXTERNAL',
       r.technician_consultant_id = c.id,
       r.technician               = c.name
 WHERE r.technician IS NULL
    OR TRIM(r.technician) = ''
    OR r.technician = c.name;
