-- Tracking numbers are issued by the system, once, and are unique across movements and consignments.
--
-- ── Why ─────────────────────────────────────────────────────────────────────
--
-- A tracking number was free text typed into the dispatch form: optional in practice, any format,
-- and nothing stopped two journeys carrying the same one. It identifies a trip - a movement
-- dispatched on its own, or a consignment, whose number every movement riding on it shares - so
-- it is issued per trip and registered here.
--
-- ── The registry ────────────────────────────────────────────────────────────
--
-- `id` is the sequence. The formatted number, TRK-<year>-<id, 6 digits>-<check digit>, is written
-- straight after the row is inserted, which is why `number` is nullable: MySQL treats NULLs as
-- distinct in a unique index, so a row mid-issue never collides, and two people dispatching in the
-- same second each get their own row from AUTO_INCREMENT. The unique constraint is the guarantee;
-- the sequence is only what makes it never need to fire.
--
-- `status` is SUPERSEDED for a number a movement held of its own before it was loaded onto a
-- consignment, which then gives it the consignment's number instead. Kept rather than deleted, so a
-- gap in the sequence is always explained.
--
-- Not audited: the registry is append-only bookkeeping with its own issuer and timestamp.

CREATE TABLE tracking_number (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    number      VARCHAR(32)  NULL,
    owner_type  VARCHAR(16)  NOT NULL,
    owner_id    BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    issued_at   DATETIME(6)  NOT NULL,
    issued_by   BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tracking_number_number UNIQUE (number),
    KEY idx_tracking_number_owner (owner_type, owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── The courier's own number ────────────────────────────────────────────────
--
-- The free-text box survives as what it usually held: the courier's waybill or reference. No
-- uniqueness - it is somebody else's number. Both tables are @Audited, so their _aud tables need the
-- column too or every write fails (trap #5).

ALTER TABLE movement          ADD COLUMN courier_reference VARCHAR(255) NULL;
ALTER TABLE movement_aud      ADD COLUMN courier_reference VARCHAR(255) NULL;
ALTER TABLE consignment       ADD COLUMN courier_reference VARCHAR(255) NULL;
ALTER TABLE consignment_aud   ADD COLUMN courier_reference VARCHAR(255) NULL;

-- Existing typed tracking numbers are left exactly as they are - visible and searchable, but not
-- registered and not held to the format.
