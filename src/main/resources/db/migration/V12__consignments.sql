-- Consignments: one physical journey carrying many movements.
--
-- Separates the journey (courier, tracking, dispatch, arrival) from the obligation (these items,
-- owed to this person, under this approval). One van can carry stock for several requests to the
-- same branch, which a single `request_id` on `movement` could never express.
--
-- Existing movements are left exactly as they are: `consignment_id` is nullable, and anything
-- already in flight keeps its own logistics columns and its own lifecycle. No backfill — rewriting
-- completed journeys would invent a history that never happened.

CREATE TABLE IF NOT EXISTS consignment (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    reference              VARCHAR(40)  NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    source_location_id     BIGINT       NULL,
    dest_location_id       BIGINT       NULL,
    landing_store_id       BIGINT       NULL,

    courier_id             BIGINT       NULL,
    courier_service        VARCHAR(255) NULL,
    tracking_number        VARCHAR(255) NULL,
    plate_number           VARCHAR(255) NULL,
    dispatch_date          DATETIME(6)  NULL,
    expected_delivery_date DATETIME(6)  NULL,
    arrival_date           DATETIME(6)  NULL,

    remarks                VARCHAR(255) NULL,
    initiator_id           BIGINT       NULL,
    receiving_officer_id   BIGINT       NULL,

    create_date            DATETIME(6)  NULL,
    last_modified          DATETIME(6)  NULL,
    created_by             BIGINT       NULL,
    last_modified_by       BIGINT       NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_consignment_reference UNIQUE (reference),
    CONSTRAINT fk_consignment_source_location FOREIGN KEY (source_location_id) REFERENCES branch (id),
    CONSTRAINT fk_consignment_dest_location   FOREIGN KEY (dest_location_id)   REFERENCES branch (id),
    CONSTRAINT fk_consignment_landing_store   FOREIGN KEY (landing_store_id)   REFERENCES store (id),
    CONSTRAINT fk_consignment_courier         FOREIGN KEY (courier_id)         REFERENCES courier (id)
) ENGINE = InnoDB;

-- Envers mirror. Every new column on an @Audited entity needs its counterpart here or the whole
-- surrounding transaction rolls back at runtime with "Unknown column ... in 'field list'" — the
-- same failure that silently killed stocking when the stock audit table fell behind.
CREATE TABLE IF NOT EXISTS consignment_aud (
    id                     BIGINT       NOT NULL,
    rev                    INT          NOT NULL,
    revtype                TINYINT      NULL,
    reference              VARCHAR(40)  NULL,
    status                 VARCHAR(20)  NULL,
    courier_service        VARCHAR(255) NULL,
    tracking_number        VARCHAR(255) NULL,
    plate_number           VARCHAR(255) NULL,
    dispatch_date          DATETIME(6)  NULL,
    expected_delivery_date DATETIME(6)  NULL,
    arrival_date           DATETIME(6)  NULL,
    remarks                VARCHAR(255) NULL,
    create_date            DATETIME(6)  NULL,
    last_modified          DATETIME(6)  NULL,
    created_by             BIGINT       NULL,
    last_modified_by       BIGINT       NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_consignment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS consignment_delivery_documents (
    consignment_id BIGINT       NOT NULL,
    document       VARCHAR(255) NULL,
    CONSTRAINT fk_consignment_documents FOREIGN KEY (consignment_id) REFERENCES consignment (id)
) ENGINE = InnoDB;

-- Link movements to their journey.
ALTER TABLE movement ADD COLUMN consignment_id BIGINT NULL;
ALTER TABLE movement ADD CONSTRAINT fk_movement_consignment
    FOREIGN KEY (consignment_id) REFERENCES consignment (id);

CREATE INDEX idx_movement_consignment ON movement (consignment_id);
CREATE INDEX idx_consignment_status_dest ON consignment (status, dest_location_id);
