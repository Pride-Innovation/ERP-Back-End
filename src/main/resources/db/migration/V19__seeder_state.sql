-- A record of which one-time seeding steps have already run.
--
-- Seeders in this application fall into two kinds, and the difference matters. Most are
-- reconciliations: "this permission should exist", "this role should be linked to this unit" — safe
-- to re-run because the desired state never changes. A few are migrations wearing a seeder's
-- clothes: "on the day this ships, give every existing role a default scope so nobody is narrowed".
-- Those must run once and never again, because after that the estate belongs to whoever administers
-- it, and re-running silently undoes their decisions.
--
-- UnitRoleSeeder had no way to tell the two apart, so its scope grants were re-applied on every
-- startup: revoking DASH_SCOPE_BRANCH from MANAGER in Settings worked until the next restart, then
-- quietly came back. Nobody would notice until an audit.
--
-- Existing seeders infer "already done" from a side effect — the permission row's absence, the
-- unit-role link. That works where a side effect happens to exist and is unavailable where it does
-- not. This table is the explicit version, and it can be reused by any future one-time step.

CREATE TABLE seeder_state (
    marker     VARCHAR(120) NOT NULL,
    applied_at DATETIME     NOT NULL,
    note       VARCHAR(500) NULL,
    PRIMARY KEY (marker)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- VARCHAR primary key rather than a generated id, deliberately: the marker IS the identity, there is
-- nothing to generate, and it sidesteps the GenerationType.AUTO problem that forced V18's data
-- seeding out of Flyway and into Java in the first place.

-- Not Envers-audited. This is bookkeeping about deployments, not a record of anything a person did;
-- the grants it governs are audited where they happen.
