-- Which locations may stock what.
--
-- ── Why ─────────────────────────────────────────────────────────────────────
--
-- Any branch could stock anything: the stocking form offered every category to everyone and the
-- server checked no rule. Measured when this was written (42 orders, 12 categories, 85 commodities,
-- 54 branches): branches had only ever stocked consumables - Gulu stationery, printing & branding,
-- cleaning & hygiene and kitchen & groceries; Mbarara stationery - while computers, furniture,
-- equipment and building materials had only ever been stocked at Head Office.
--
-- ── The model ───────────────────────────────────────────────────────────────
--
-- A rule lives on the category, and every commodity in it follows the rule unless the commodity
-- sets its own (`stocking_override`). A rule is three things: Head Office, all branches, and a list of
-- specific branches (ignored while all branches is on). Nothing ticked means nobody may stock it.
--
-- `asset_type` and `commodity` are not audited, so there are no _aud tables to extend. Changes to a
-- rule are recorded in audit_event (STOCKING_RULE_CHANGED) instead.

ALTER TABLE asset_type
    ADD COLUMN stock_at_head_office  BIT NOT NULL DEFAULT 1,
    ADD COLUMN stock_at_all_branches BIT NOT NULL DEFAULT 0;

ALTER TABLE commodity
    ADD COLUMN stocking_override     BIT NOT NULL DEFAULT 0,
    ADD COLUMN stock_at_head_office  BIT NOT NULL DEFAULT 1,
    ADD COLUMN stock_at_all_branches BIT NOT NULL DEFAULT 0;

CREATE TABLE asset_type_stocking_branch (
    asset_type_id BIGINT NOT NULL,
    branch_id     BIGINT NOT NULL,
    PRIMARY KEY (asset_type_id, branch_id),
    CONSTRAINT fk_atsb_asset_type FOREIGN KEY (asset_type_id) REFERENCES asset_type (id) ON DELETE CASCADE,
    CONSTRAINT fk_atsb_branch     FOREIGN KEY (branch_id)     REFERENCES branch (id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commodity_stocking_branch (
    commodity_id BIGINT NOT NULL,
    branch_id    BIGINT NOT NULL,
    PRIMARY KEY (commodity_id, branch_id),
    CONSTRAINT fk_csb_commodity FOREIGN KEY (commodity_id) REFERENCES commodity (id) ON DELETE CASCADE,
    CONSTRAINT fk_csb_branch    FOREIGN KEY (branch_id)    REFERENCES branch (id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── Day one: mirror what has actually been stocked ──────────────────────────
--
-- Every category stays stockable at Head Office (the column default). A category any branch has
-- ever stocked becomes stockable at all branches, so nobody loses something they already use; the
-- rest are Head Office only from day one.
--
-- Stated as a rule over the data, not as a list of category names: names are edited in Settings and
-- differ between environments, and a list would silently match nothing elsewhere. Flyway runs this
-- once, so it never undoes a decision made in Settings afterwards.

UPDATE asset_type at
   SET at.stock_at_all_branches = 1
 WHERE EXISTS (
     SELECT 1
       FROM stock s
       JOIN branch b           ON b.id = s.branch_id AND b.is_head_office = 0
       JOIN stock_commodity sc ON sc.stock_id = s.id
       JOIN commodity c        ON c.id = sc.commodity_id
      WHERE c.asset_type_id = at.id
 );
