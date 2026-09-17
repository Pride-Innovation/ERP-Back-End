# Testing guide — branch scoping and unit permissions

How to verify the whole of the unit-permissions work by hand: which permissions to grant, from
which screen, to whom, and what each person should then see.

Allow about 90 minutes. Work top to bottom — the setup sections have to happen before the persona
walkthroughs mean anything.

**Every refusal below is a pass.** Roughly half these checks exist to confirm something is now
*blocked*, so a 403 or a red toast where this guide says to expect one is the feature working.

---

## Before you start: three things that will look like bugs

**1. Officers now see fewer assets.** The assets list used to show the whole branch. At
`DASH_SCOPE_SELF` it shows only assets assigned to that person. This is the intended change and the
main point of the work.

**2. DIRECTOR and ROLE_ADMIN now see fewer requests.** Neither role was named in the deleted
role-name switch, so both fell through a `default` branch that returned "no restriction" — they saw
every request in the bank. They now see their own reporting line. If either genuinely needs more,
grant a scope in Settings rather than reverting the change.

**3. The UI updates at next sign-in.** The backend enforces a permission change immediately; the
front end reads the permission set from the login payload. After changing what a unit or role
grants, **sign out and back in** before judging what is on screen. If a control is still there but
its endpoint refuses, that is the login payload being stale, not a gating bug.

---

## 1 · Start-up and seeding

Restart the backend and watch the log. `UnitRoleSeeder` runs from `DataInitializer`, after every
Flyway migration.

| Check | Expect |
|---|---|
| Backend starts | No `Detected failed migration`, no `V18` error. The `unit_role` table exists. |
| First run only, in the log | `Creating permission: VIEW_ALL_BRANCH_ASSETS` … (eight of them), then `Creating unit role ADMIN_UNIT`, `Creating unit role INFRA_UNIT`, then `Attached role ADMIN_UNIT to unit 'Admin Unit'`. |
| Second run | Silent. The seeder is idempotent; nothing should be recreated. |

Then sign in as **Super Admin** and confirm the estate:

**Settings → Roles and Permissions**

- `ADMIN_UNIT` and `INFRA_UNIT` appear in the role list.
- Opening either shows a group called **Cross-branch visibility** containing the eight multipliers.
  They must *not* be sitting under **Other permissions**.
- Each multiplier's description says it **widens** a permission rather than granting one. The four
  `MANAGE_*` entries are red.
- Both unit roles start with the same seven permissions. They are seeded as peers on purpose; you
  narrow them from this screen.

**Settings → Units**

- **Admin Unit** and **Infra Unit** each show a red chip reading `1 role · cross-branch`.
- Every other unit shows no role chip at all.

---

## 2 · The scope model in one page

Two independent axes. Understanding this makes the rest of the guide read quickly.

### Axis 1 — what you may do (`READ_ASSET`, `UPDATE_ASSET`, `APPROVE_REQUEST`, …)

Granted on a role in **Settings → Roles and Permissions**. Opens endpoints. Says nothing about
*whose* records.

### Axis 2 — how far that reaches

Three settings, widest wins:

| Scope | Granted by | Means |
|---|---|---|
| `SELF` | `DASH_SCOPE_SELF` | Only records this person holds or raised |
| `BRANCH` | `DASH_SCOPE_BRANCH` | Everything at their duty station |
| `ALL` | a **multiplier** | Every branch and Head Office |

The eight multipliers are per subject and per action:

```
VIEW_ALL_BRANCH_ASSETS       MANAGE_ALL_BRANCH_ASSETS
VIEW_ALL_BRANCH_REQUESTS     MANAGE_ALL_BRANCH_REQUESTS
VIEW_ALL_BRANCH_MOVEMENTS    MANAGE_ALL_BRANCH_MOVEMENTS
VIEW_ALL_BRANCH_INVENTORY    MANAGE_ALL_BRANCH_INVENTORY
```

**A multiplier grants nothing on its own.** `VIEW_ALL_BRANCH_ASSETS` does not open the assets page —
`READ_ASSET` does that. The multiplier changes how much of the register you see once you are on it.
Someone holding only multipliers can do nothing at all.

**One asymmetry worth knowing:** `DASH_SCOPE_ALL` widens **viewing** to every branch but never
**changing**. Without that, a dashboard widget counting 1,200 assets would link to a page showing 90.
A cross-branch *write* always waits for the explicit `MANAGE_ALL_BRANCH_*`.

### Where a person's permissions come from

```
title.role.permissions  ∪  additionalRoles[].permissions  ∪  unit.roles[].permissions
```

The third is the new part. Nothing changes on a user's own record when their unit is granted a
role — which is why the dashboard names the unit, and why granting is audited.

---

## 3 · Accounts to prepare

Four sign-ins cover everything. Set them all up now.

Also note down, from the Super Admin session: **one asset id and one request id belonging to each of
two different branches**. Several checks work by naming a record you should not be able to reach.

| # | Account | How to set it up | Tests |
|---|---|---|---|
| A | **Branch officer** | Title with role `OFFICER`, duty station = Branch A. Grant `READ_ASSET`, `UPDATE_ASSET`, `CREATE_ASSET`, `IMPORT_ASSET`, `READ_REQUEST`, `CREATE_REQUEST` to `OFFICER`. | SELF scope, and the write guard |
| B | **Branch manager** | Title with role `BRANCH_MANAGER`, duty station = Branch A. | BRANCH scope |
| C | **Admin Unit officer** | Title with role `OFFICER` — *the same role as A* — and **Unit = Admin Unit**. Duty station Head Office or Branch A. | What the unit confers |
| D | **Super Admin** | Existing. | Settings, granting, revoking, override |

**Setting the unit (account C):** Users → edit the user → the **Unit (optional)** field → Admin Unit.
That is the only change; do not give them any extra role.

> Account C is the whole experiment. Same title as account A, same permissions on that title —
> the only difference is unit membership. If C can do more than A, the feature works.

---

## 4 · Branch isolation on assets

The core change. Reading *and* writing are now checked against the record, not just the endpoint.

The by-id checks matter most: before this work, a branch officer could not **find** another branch's
asset through the listing, but could **edit** it if they knew its id.

### As A (branch officer)

| Do this | Expect |
|---|---|
| Open the assets list | Only assets assigned to them — **not** the whole branch |
| Open one of their own assets | Opens normally |
| Navigate directly to another branch's asset detail URL | **403** — not the record |
| `PUT /assets/{id}` on that same foreign id | **"You can only make changes to records at your own branch."** This succeeded before today |
| Edit a colleague's asset **at their own branch** | **Refused.** At SELF scope, holding `UPDATE_ASSET` does not make someone else's asset theirs |
| Create an asset, choosing another branch in the form | **Refused** — on the branch, not on `CREATE_ASSET` |
| Reassign, send-to-store, or change the image of a foreign asset | **Refused** on each |
| Dispose of a foreign asset (`POST /movements/disposal`) | **Refused.** Disposal writes an asset off permanently and had no record check at all before |
| Open a foreign asset's **Assignment History** by id | **Refused.** The trail leaks what the record leaks — every holder, every date |
| Revoke `READ_ASSIGNMENT_HISTORY` from their role, sign back in | The **Assignment History tab is gone** — not present and failing. Repair History stays |
| Revoke `READ_REPAIR_HISTORY` too | Only **Details** remains |
| Re-grant both | Both tabs return at next sign-in |
| Open a foreign asset's **Repair History** by id | **Refused** |
| `GET /assignment-history` with **no** asset id | Only their own branch's rows. Unscoped this was the whole estate's chain of custody |
| The **maintenance report** | Only their own branch's repairs |

### Bulk import (as A)

Prepare a spreadsheet with **three rows at Branch A and one naming Branch B**.

| Expect |
|---|
| The three good rows import |
| The Branch B row is reported as a row error: **"You cannot import assets for 'Branch B'. You may only import into your own branch unless your unit grants cross-branch asset management."** |
| The batch is **not** rejected wholesale — the result reads 3 inserted, 1 failed |

### As B (branch manager)

| Do this | Expect |
|---|---|
| Open the assets list | The **whole branch**, not just their own |
| Edit a colleague's asset at Branch A | Works |
| Open or edit any Branch B asset by id | **Refused** |

> The branch-manager case is where an over-tight guard would show up as lost function. If B cannot
> see their own branch, `DASH_SCOPE_BRANCH` is missing from `BRANCH_MANAGER` — check Settings.

---

## 5 · What the unit confers

Sign in as **C** (Admin Unit officer). Same title as A.

| Do this | Expect |
|---|---|
| Open the assets list | Assets at **every** branch |
| Edit a Branch B asset | Works — via `MANAGE_ALL_BRANCH_ASSETS` from the unit role |
| Import a spreadsheet naming several branches | All rows land |
| Look at the dashboard hero | **"All branches & Head Office · via Admin Unit"** |
| Open the requests list | **Only their own line** — not every branch's requests |

### The dashboard widgets must all load

Open the browser network tab on C's dashboard. **No request should return 403.** In particular
`GET /dashboard-asset-report/by-branch` and `/dashboard-asset-report`, which feed the "Assets Across
All Branches" heatmap.

Those two named only the legacy `VIEW_ALL_BRANCHES` permission — a unit confers ALL scope through
`DASH_SCOPE_ALL` and `VIEW_ALL_BRANCH_ASSETS` and never through the legacy one, so the widget
rendered and its endpoint refused. `DashboardWidgetEndpointTest` now pins every widget's endpoint
against the narrowest authority the front end renders it for.

### Two things to verify about the label

**It names the unit only when the unit is the reason.** Grant `DASH_SCOPE_ALL` directly to the
`OFFICER` role, sign C back in: the `· via Admin Unit` suffix should **disappear**, because their
reach no longer depends on the unit. Revoke it again to restore.

**Requests are deliberately excluded.** `VIEW_ALL_BRANCH_REQUESTS` is not granted by default. A
branch request reaches Admin through the workflow, which sets `Request.currentUnit` on the step —
not by being visible from the moment it was raised. To confirm the mechanism exists, grant
`VIEW_ALL_BRANCH_REQUESTS` to `ADMIN_UNIT`, sign C back in, and every branch's requests appear.
**Revoke it again** before continuing.

---

## 6 · Granting and revoking on a unit

**Settings → Units**, shield icon on the card. Gated on `UPDATE_ROLE`.

### The dialog

1. **Step one — pick roles.** Any role carrying a cross-branch permission wears a red
   **Cross-branch** chip in the list.
2. **Step two — review consequences.** Granted and revoked shown separately, each listing what
   members gain or lose **in plain English**, not as permission names. Cross-branch gains get their
   own warning block at the top and turn the confirm button red.

### Checks

| Do this | Expect |
|---|---|
| Untick `ADMIN_UNIT`, click **Review changes** | *Revoking ADMIN_UNIT* and the seven things members lose, in plain English |
| Apply, then sign C back in | Their cross-branch reach is gone; they behave exactly like A |
| Look at the Units page | The red cross-branch chip on the Admin Unit card is gone |
| **Restart the backend**, sign C back in | The revocation **survives**. The seeder is create-only where it matters |
| Re-grant `ADMIN_UNIT` | Warning block appears, confirm button is red. C's reach returns at next sign-in |
| Tick a role whose permissions the unit already confers | The dialog says *"These roles carry no permissions the unit does not already confer"* |
| Revoke a role that is duplicated by another | *"Nothing is lost — the remaining roles still confer everything these did"* |
| Untick **everything** and apply | Every role revoked. This is the requirement that all permissions be revokable |

### The guard on the dialog itself

Create a user with `UPDATE_SETTING` but **not** `UPDATE_ROLE`:

- The shield icon does not render on any unit card.
- `PUT /units/{id}/roles` called directly returns **403**.

Renaming a unit does not qualify someone to change what it grants.

### Audit

**Audit Trails** should show `UNIT_ROLES_CHANGED` for each save, naming what was granted and
revoked — readable without the roles table open beside it. A save that changes nothing is still
recorded, at INFO rather than WARNING.

---

## 7 · Who may act on a workflow step

The engine used to ask only *what* was being done, never *by whom*. Anyone who could approve
anything could approve everything.

**The permissive cases below matter more than the refusals.** A check that refuses too readily
strands a request, and a stranded request stops work with nobody able to clear it.

### Refusals

| Do this | Expect |
|---|---|
| Approve a request currently sitting with someone else | **"This request is waiting on [name], not on you."** |
| Act on a step routed to a unit you are not in | **"This request is with the Admin Unit, and you are not a member of it."** |

### Must still work

| Do this | Expect |
|---|---|
| The named approver approves their own step | Works |
| **Any** member of the routed unit picks up a unit step | Works — that is what routing to a unit is for |
| A request created **before the workflow engine** (no `currentApprover`, no `currentUnit`) | **Still actionable.** This is the stranding case and the single most important check here |
| The requester acknowledges receipt | Works — the `REQUESTER` step type resolves to them |
| A stand-in for someone on leave acts | Works — leave substitution resolves the acting user into `currentApprover` when the step is assigned |

### The override

Grant `MANAGE_ALL_BRANCH_REQUESTS` to a role and confirm that holder can act on any step. The
backend log records the override. Super Admin holds this like anyone else, so it needs no special
case.

### One more

Approving and rejecting from the UI should behave exactly as before. The approver id must now match
the signed-in user — the front end already sent its own, so nothing visible changes. Check the step
log attributes the decision to whoever actually acted.

---

## 8 · Requests, after the old model was deleted

The role-name switch (`determineUserRole`) is gone. Check **all four tabs** — All, Pending,
Rejected, Issued — for each persona. The deleted stationery predicates used to interfere
specifically with Pending and Issued.

| Persona | Expect across all four tabs |
|---|---|
| A (officer) | Their own requests and their subordinates' |
| B (branch manager) | The whole branch, nothing beyond |
| C (Admin Unit) | Their own line, plus anything routed to Admin Unit |
| DIRECTOR / ROLE_ADMIN | **Their reporting line** — previously *everything in the bank* |

Two specific cases:

- **A BOM listed as current approver on a branch non-stationery request** (e.g. Computers) should
  now see it. The deleted predicate hid exactly this — the request matched neither side of
  `requester.branch = Head Office OR isBranchStationery = true`.
- **Exports must match what is on screen**, per tab and per persona. An export that ignores scoping
  is the quiet version of this bug.

---

## 9 · Movements, stores and inventory

A movement touches up to **four** branches — a store's location or a person's duty station at either
end — and no movement has all four. The rule is that **any** end being yours puts it in reach, which
is exactly what the movements listing has always done.

| Do this | Expect |
|---|---|
| B receives a movement Head Office → Branch A | Works. One end is theirs |
| B dispatches a movement between Branch B and Branch C, by id | **Refused** |
| B opens that movement's detail page | **Refused** |
| C does either | Works — the unit grants `MANAGE_ALL_BRANCH_MOVEMENTS` |
| **Issuance and consignment fulfilment across branches** | **Still work.** `createMovement` is deliberately unguarded — a Head Office issuer sending stock to a branch is the ordinary case. If this broke, a guard went on the wrong method |
| B views stores and stock balances | Branch A only |
| C views the same | Every branch |
| B creates a store at another branch's location | **Refused.** Reporting on another branch's stock is not a licence to reorganise its stores |

### Store page filters

| Do this | Expect |
|---|---|
| Type in **Stock Name** | Filters, partial and case-insensitive. It silently did nothing before — the endpoint never declared the parameter |
| Look for **Stock Date** | Gone. `createdAt` was never a parameter here |
| Set **Reorder at** = 50 on a commodity holding 30 | The Status column now reads **low** or **warning**, and the same line appears under Inventory → low stock. It read *"in stock"* before, because the column ignored the threshold and used its own under-5 / 5–10 bands |
| A commodity with **no** reorder level | Reads **not monitored** in grey, and is never flagged however little is on hand |
| Filter by each of the three **Stock Level** values | The three counts sum to the unfiltered total, with nothing left over |
| Page through a branch with more than one page | No row appears twice, none goes missing. The listing had no ordering at all before |
| Clear the category filter | Uncategorised stock still appears. If lines vanish, a LEFT JOIN went missing |

---

## 10 · Newly guarded reads

These were reachable by **anyone with a login**. The risk here is over-tightening, so check the
pages that depend on them as well as the refusals.

| Endpoint | Without the permission | With it |
|---|---|---|
| `GET /assignment-history` | **403** without `READ_ASSET`. This was the chain of custody for the whole estate | The asset's Trails tab still loads |
| `GET /inventory/stores` | **403** holding none of `READ_STORE`, `READ_INVENTORY`, `RECEIVE_ASSET_IN_STORE`, `CREATE_MOVEMENT`, `PERFORM_STOCK_TAKE` | The **"Receive into store"** picker still populates for someone holding only `RECEIVE_ASSET_IN_STORE`; stock take and the movement builder still list stores |
| Approval workflows (create / edit / toggle / delete) | **403** without `UPDATE_SETTING`. Every one was open to any signed-in user — enough to retarget a step at yourself, or delete a workflow so requests skipped approval entirely | Works with `UPDATE_SETTING`; viewing needs only `READ_SETTING` |

The store directory is guarded by a **disjunction** because five unrelated jobs need it. Naming a
single permission would have broken the assets page's store picker for someone who legitimately has
no store permissions.

---

## 11 · Front-end gating

Row menus are now filtered centrally: an option names the permission its endpoint demands, and the
shared table drops what the viewer does not hold.

For **each account**, open the row menu on each page and confirm no option produces a 403.

| Page | Hidden without |
|---|---|
| Users → row menu | `UPDATE_USER` (Update); `CREATE_USER` (Enable, Unblock, Disable, Block) |
| Requests → Rejected tab | `DELETE_REQUEST`, `UPDATE_REQUEST` — these were offered to everyone here while the other tabs gated them |
| Transport requests | `DELETE_REQUEST`, `UPDATE_REQUEST` |
| Inventory | `UPDATE_INVENTORY` (Receive Delivery, Correct Details), `DELETE_INVENTORY` (Delete) |
| GRN documents | `UPDATE_INVENTORY` (Upload Signed GRN) |
| Asset repair history | `REPAIR_ASSET` (Complete Repair) |
| Movements page | `CREATE_MOVEMENT` (New Movement button) |
| Settings → Approval Workflows | `UPDATE_SETTING` (New Workflow button) |
| Assets → row menu | Each of `UPDATE_ASSET`, `REASSIGN_ASSET`, `REPAIR_ASSET`, `RECEIVE_ASSET_IN_STORE`, `DISPOSE_ASSET` |

**Then walk every page as Super Admin and confirm the full menu is still there.** Hiding a control
someone is entitled to is the harder failure to notice — nothing on screen says why it is missing.

> **Known oddity, not a bug in the sweep:** the four user account actions (Enable, Unblock, Disable,
> Block) answer to `CREATE_USER`, because they are POSTs under `/users/**` and the matcher maps POST
> to the create permission. Blocking someone who already exists is not a creation. The front end
> mirrors the endpoint rather than guessing — gating on `UPDATE_USER` would hide a control the API
> would in fact allow. Worth correcting on the backend.

---

## 12 · PDF exports

There is now **one** exporter. `utils/pdf.js` is deleted.

Export from a **DataGrid** screen — Repairs & Maintenance, GRN documents, assignment history, or any
report — and from a **list** page. Both should now carry:

- repeating column headings on every page,
- a filter summary under the header saying which slice of the data it is,
- page numbers.

The DataGrid screens are the ones whose output actually changes.

---

## 13 · Audit trail

Confirm the decisions above left a record.

| Expect a row for |
|---|
| `UNIT_ROLES_CHANGED` — every unit role save, naming what moved |
| Asset writes, disposals, imports |
| Approvals and rejections, attributed to the person who **actually acted** — the endpoint used to file the decision under whatever id the client sent |
| Exports — data leaving the building is audited; ordinary page views are not |

---

## If something fails

Capture **the account, the page, and the exact message** before moving on. The message text is
usually enough to locate the guard that produced it:

| Message | Comes from |
|---|---|
| *"You can only make changes to records at your own branch."* | `AccessScopeService.requireCanManage` |
| *"You can only act on records that touch your own branch."* | `requireCanManageAny` — a movement |
| *"You can only view data for your own branch."* | `resolveBranch`, when a branch was named explicitly |
| *"This request is waiting on … , not on you."* | `WorkflowEngineService.requireActorIsRouted` |
| *"This request is with the … , and you are not a member of it."* | the same, unit branch |
| *"You cannot import assets for '…'."* | the per-row import guard |
| *"You can only approve or reject as yourself."* | `RequestReportService`, client-supplied approver id |

A plain **403** with no message is the Spring Security filter chain refusing before the handler ran —
that is a route rule (`@PreAuthorize` or a matcher in `SecurityConfiguration`), not a scope check.

---

## Reference

- `docs/UNIT_PERMISSIONS.md` — the decisions behind this model
- `CLAUDE.md` — the permission model, the traps, and what remains open
- `AccessScopeService` — the one resolver for "whose records may I touch, and where"
- `EndpointGuardCoverageTest` — the endpoints still reachable by any signed-in user. The list is
  meant to shrink and must not be added to without a reason
