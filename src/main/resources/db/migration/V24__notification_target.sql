-- A notification should say what it is about, not where the browser should go.
--
-- ── What was here before ────────────────────────────────────────────────────
--
-- `link` held a fully-formed frontend URL, built in Java: `frontEndUrl + "/assets-mgt/..."`. Two
-- things were wrong with that, and the data shows both:
--
--   * **744 of 760 rows pointed at a route that does not exist.** They read
--     `/assets-mgt/asset-request/view?id=37` — a query parameter — while the route is
--     `/asset-request/view/:id`, a path segment. Every one of those would have landed on the detail
--     page with no id.
--   * **All 760 were absolute**, including the host from `frontEndUrl`. A URL like that cannot be
--     handed to the router, and it is wrong the moment the application is served from anywhere other
--     than the host that was configured when the row was written.
--
-- Neither was noticed because nothing ever followed the link: the notifications page and the bell
-- both only marked things read. So the column was written 760 times and read zero.
--
-- ── Why a target rather than a corrected URL ────────────────────────────────
--
-- Fixing the format would have left the backend owning frontend routes, which is what produced 744
-- dead links in the first place — Java cannot know when a route is renamed. `entity_type` and
-- `entity_id` say what the notification concerns; resolving that to a route is the frontend's job,
-- in one place, against its own route table.
--
-- ── The backfill ───────────────────────────────────────────────────────────
--
-- Unambiguous per type, which is why it can be done in SQL rather than by hand: the STEP_* and
-- WORKFLOW_* notifications are raised by the workflow engine and carry a request id; the MOVEMENT_*
-- ones are raised by the movement approval service and carry a movement id in the same column. That
-- column was called `request_id` for both, so for 69 rows its name was simply wrong — which is the
-- other half of what this migration repairs.

ALTER TABLE app_notification
    ADD COLUMN entity_type VARCHAR(40) NULL,
    ADD COLUMN entity_id   BIGINT      NULL;

UPDATE app_notification
   SET entity_type = CASE
           WHEN type LIKE 'MOVEMENT%' THEN 'MOVEMENT'
           ELSE 'REQUEST'
       END,
       entity_id = request_id
 WHERE request_id IS NOT NULL;

-- Rows with no id at all keep a null target: the frontend renders those as plain text rather than as
-- something to click. Better than a link that goes nowhere, which is what 744 of these were.

CREATE INDEX idx_app_notification_target ON app_notification (entity_type, entity_id);

-- `link` and `request_id` are dropped rather than left in place. A column called `request_id` sitting
-- beside `entity_id` is exactly what somebody later writes a movement id into again, and a `link`
-- column beside a resolver is an invitation to go back to baking URLs into the database. Nothing is
-- lost: every value in both is reproduced by the two columns above.
ALTER TABLE app_notification DROP COLUMN link;
ALTER TABLE app_notification DROP COLUMN request_id;
