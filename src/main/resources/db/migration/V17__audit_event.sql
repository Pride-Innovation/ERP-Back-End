-- The activity log behind the Audit Trails page.
--
-- Hibernate Envers already mirrors nineteen entities into _aud tables and none of it can serve this
-- page: revinfo records a revision number and a timestamp but no actor and no IP address, signing in
-- changes no entity so logins and logouts are invisible to it entirely, and a revision can say
-- "status_id went 12 to 13" but never "approved movement #21". Envers stays as the field-level
-- forensic record; this table is the narrative.
--
-- Append-only. Nothing in the application updates or deletes a row here, and there is no HTTP path
-- that writes one directly — events are recorded by the services that perform the actions.
--
-- Deliberately NOT Envers-audited, so there is no audit_event_aud: mirroring this would double every
-- write in the application to track changes to a table that never changes.

CREATE TABLE audit_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT,

    occurred_at  DATETIME(6)  NOT NULL,

    -- CREATED / UPDATED / DELETED / LOGIN / LOGOUT / APPROVED / REJECTED / SYSTEM.
    -- Stored as the enum name rather than an ordinal: an ordinal would silently re-map every
    -- historical row the day someone inserts a value into the middle of the enum.
    event_type   VARCHAR(30)  NOT NULL,

    -- ASSETS / INVENTORY / USERS / STORE / REQUESTS / MOVEMENT / DISPOSAL / MAINTENANCE / SYSTEM
    module       VARCHAR(40)  NOT NULL,

    -- Machine-readable code, e.g. MOVEMENT_APPROVAL_BYPASSED. Separate from description because the
    -- sentence is written for a person and will be reworded; anything counting keys off this.
    action       VARCHAR(80)  NOT NULL,

    description  VARCHAR(500) NOT NULL,

    -- INFO / WARNING / CRITICAL. Set at the call site, not derived.
    severity     VARCHAR(16)  NOT NULL,

    -- Nullable: failed logins, seeders and scheduled jobs have no signed-in actor.
    --
    -- No foreign key, on purpose. This is an append-only historical record and must survive the
    -- removal of a user account; a FK would either block that deletion or cascade away the evidence.
    actor_id     BIGINT       NULL,

    -- Copied, not joined. Reading the name back through the user table would mean that renaming
    -- someone silently rewrites who did what years ago.
    actor_name   VARCHAR(150) NULL,
    actor_email  VARCHAR(150) NULL,

    -- The record affected, for a per-record history view. Loose reference by type + id rather than a
    -- FK per module, since one table points at assets, movements, users, stores and more.
    entity_type  VARCHAR(60)  NULL,
    entity_id    BIGINT       NULL,

    -- 45 characters is the longest possible IPv6 literal including an embedded IPv4 tail.
    ip_address   VARCHAR(45)  NULL,
    user_agent   VARCHAR(255) NULL,

    -- The actor's branch at the time; the hook for scoping this page per branch later.
    branch_id    BIGINT       NULL,

    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The default view is newest-first and every filter narrows within a date range.
CREATE INDEX idx_audit_event_occurred ON audit_event (occurred_at);
CREATE INDEX idx_audit_event_type     ON audit_event (event_type);
CREATE INDEX idx_audit_event_module   ON audit_event (module);
CREATE INDEX idx_audit_event_actor    ON audit_event (actor_id);
-- "everything that ever happened to asset 412"
CREATE INDEX idx_audit_event_entity   ON audit_event (entity_type, entity_id);

-- No backfill. Envers revinfo has no actor and no IP address, so replaying it would produce rows
-- attributed to nobody from nowhere — worse than an honest empty table. The trail starts here; the
-- existing per-entity Envers views remain the record for anything older.
