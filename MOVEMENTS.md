# Movements & Stores — End-to-End Test Guide

How to test everything that happens **from the moment an issuance is approved**: fulfilment
movements, movement approvals, repair / replacement / return / disposal flows, and the store
updates each one causes. Companion to `workflow.md` (request approval); the request → approval →
issue steps are assumed known and are not repeated here.

Each scenario lists the UI steps and a **Verify** checklist. Run them in order the first time —
later scenarios reuse assets/stock produced by earlier ones.

---

## 0. One-time test setup

| # | What | Where in the UI |
|---|---|---|
| 0.1 | **Actors.** You need: a HO requester, a branch requester, a branch BOM, a HO admin (issuer) whose `reportsTo` ladder has at least a Manager above them, and an officer with their own Supervisor/Manager ladder (movement initiator). | Users page → check each user's title & Reports To |
| 0.2 | **Repair routing per category.** Computers → Repairable ✓, destination **IT**. Furniture → Repairable ✓, destination **ADMIN**. Pick one category (e.g. Kitchen & Groceries) → **Repairable ✗** (for the auto-disposal test). Pick one (e.g. Equipment) → destination **EXTERNAL**. | Settings → Asset Categories → create/update modal → *Repairable* switch + *Default Repair Destination* |
| 0.3 | **At least one active consultant** (name + specialization). | Settings → Repair Consultants |
| 0.4 | **Stock in the HO ADMIN store**: a consumable commodity (e.g. stationery, qty ≥ 20) and 2–3 serialized assets (Computers) marked ready for issuance. | Inventory → stock items; Assets → update status |
| 0.5 | **Reorder threshold** on one consumable (e.g. 10) so the low-stock path can trigger later. | Store page → *Consumable Balances* panel → "Reorder at" |

**Reading results:** the Store landing page (`/assets-mgt/store`) shows per-branch line counts and
the Consumable Balances panel; each store page (`/assets-mgt/store/admin`, `/it`, `/disposal`)
shows only **its own** store's holdings ("Inventory by category" cards + per-category table),
switchable by branch via the Branch filter.

---

## 1. Scenario A — HO requester (same location): direct issuance, **no movement**

*Precondition: an approved request from a **HO** user, issued by the HO admin.*

1. As the **issuer's manager**: Requests → open the request → **Approve Issuance**.
2. Go to Movement (`/assets-mgt/movement`).

**Verify**
- ☐ No movement was created for this request (same source & destination location → `DIRECT_ISSUANCE`).
- ☐ HO ADMIN store balance for the commodity went **down** by the issued qty (Store → Admin Store).
- ☐ The requester can **Acknowledge Receipt** on the Requests page; request completes.

---

## 2. Scenario B — Branch requester (cross-location): the full two-leg pipeline

*Precondition: an approved request from a **branch** user for a consumable, issued from HO.*

1. As the **issuer's manager**: approve the issuance (Requests page).
2. **Movement page** → *Initiated* tab: a movement `ISSUANCE_FULFILLMENT`, **HO ADMIN store →
   branch ADMIN store**, linked to the request, remarks "Auto-created at issuance approval…".
   - ☐ It is **INITIATED**, not DRAFT — request-driven fulfilment **skips movement approval**
     (the request already went through its own workflow).
3. Open the movement (view page). As a HO store officer: **Dispatch** — courier + tracking number
   are required. Click **Release Note** to print the PDF gate pass; upload the signed scan under
   **Documents**.
   - ☐ Status → DISPATCHED. Optionally mark **In Transit**.
   - ☐ Store balances have **not** changed yet (in-transit stock is never double-counted).
4. As the **branch BOM**: open the movement → **Receive** (choose `RECEIVED_OK`; on a second run
   try `RECEIVED_WITH_DISCREPANCY` + remarks).

**Verify after receive**
- ☐ Movement is COMPLETED with receiving officer + receipt date recorded.
- ☐ **HO ADMIN balance ↓, branch ADMIN balance ↑** (Store pages, filter by each branch).
- ☐ A **second leg** was auto-created: `DEPARTMENT_TRANSFER`, branch ADMIN store → the
  **requester** (a person, no destination store), INITIATED, no approval, linked to the request.

5. As the branch BOM: open the second leg → **Complete** (intra-location, one step).

**Verify**
- ☐ Branch ADMIN balance ↓ (debit-only — the recipient is a user, not a store).
- ☐ Requester **acknowledges receipt** on the Requests page; request fully closed.

---

## 3. Scenario C — Replenishment (branch restocks, no second leg)

*Same as Scenario B, but the requester is the branch's **BOM** (the branch Operations Manager
raising stock for the branch, not a person).*

1. Approve the issuance → Movement page.

**Verify**
- ☐ The auto movement is typed **REPLENISHMENT** (not ISSUANCE_FULFILLMENT).
- ☐ Dispatch → receive works as in Scenario B; on receive HO ↓ / branch ADMIN ↑.
- ☐ **No second leg** is created — the stock's destination *is* the branch store.

---

## 4. Scenario D — Standalone movement approval (the initiator's ladder)

*Any movement created by hand with "Requires approval" ON climbs the **initiator's** reporting
ladder (Supervisor → Manager → Dept Head at HO; Supervisor → BOM → Branch Manager at a branch)
before it can be dispatched.*

1. As the **officer** (initiator): Movement → **New Movement** → Department Transfer, a source
   store with stock, any destination, one commodity line, toggle **Requires approval** ON → create.
2. Movement page:
   - ☐ Movement sits in **Awaiting Approval** (DRAFT); the *Current approver* is the officer's
     first ladder tier; that person got an in-app notification.
3. Log in as the **first-tier approver**: Movement page → **Pending My Approval** inbox (or the
   movement view) → **Approve** (optional comment).
   - ☐ The approval trail on the view page records the tier; *Current approver* advances to the
     next tier (repeat until the ceiling).
4. After the **last tier** approves:
   - ☐ Status → INITIATED; it can now be dispatched/completed normally.
5. Repeat once more but **Reject** at any tier (reason required):
   - ☐ Status → CANCELLED, initiator notified, no store change ever happens.
6. Guards to try:
   - ☐ A user who is *not* the current approver gets an error trying to approve.
   - ☐ Approve/Reject is only possible while DRAFT.

---

## 5. Scenario E — Repair transfers (Movement → **Repair & Disposal** → *Repair Transfer*)

All repair transfers **always require approval** (initiator's ladder, as in Scenario D) and flip
the asset's status to **In Maintenance** immediately at creation.

### 5.1 IT-routed (Computers)
1. Repair & Disposal → Repair Transfer → search the faulty Computer by engraved number.
   - ☐ The context strip under the picker shows the category + "Default routing: IT".
2. Create → approve through the ladder → Dispatch → Receive at HO.

**Verify**
- ☐ Asset status = In Maintenance (Assets page) from the moment of creation.
- ☐ After receive: the asset is held by the **HO IT store** (Store → IT Store; it also appears in
  the IT-store pickers of the other repair flows).

### 5.2 ADMIN-routed (Furniture)
Same steps with a Furniture asset.
- ☐ Destination resolves to the **HO ADMIN store** instead of IT.
- ☐ The routing dropdown can override the category default (IT/ADMIN/EXTERNAL) per transfer.

### 5.3 EXTERNAL (consultant)
Use the category configured EXTERNAL (or override the dropdown to External Consultant).
- ☐ The modal now **requires** a consultant (from Settings → Repair Consultants) and at least one
  **signed dispatch document** — submitting without either is blocked.
- ☐ Custody store is the **HO ADMIN store**; the movement records the consultant; the uploaded
  document(s) appear on the movement's Documents panel.

### 5.4 Non-repairable category → auto-disposal
Pick an asset whose category has **Repairable ✗**.
- ☐ The modal shows a warning that the transfer will divert to the **Disposal store**; the
  routing/logistics sections disappear.
- ☐ Submitting creates a **DISPOSAL_TRANSFER** instead (continue in Scenario H).

---

## 6. Scenario F — Temporary replacement

1. Repair & Disposal → **Temporary Replacement**: pick a spare asset **from the IT store** and the
   affected user → create → complete the movement.

**Verify**
- ☐ The asset is assigned to the user as a **temporary** assignment and leaves the IT store.
- ☐ (Alternative path: a temp replacement can also be issued in the same call as the repair
  transfer via the API's `tempAssetId` + `affectedUserId`.)

---

## 7. Scenario G — Return after repair (+ reclaim the loaner)

1. Repair & Disposal → **Return After Repair**: pick the repaired asset (source = wherever it now
   sits — IT, Admin or external custody; resolved automatically), the destination location,
   optionally the user to re-assign to, and optionally the temp asset to pull back. Requires
   approval → approve → dispatch → receive at the branch.

**Verify**
- ☐ The repaired asset is re-assigned to the user (or lands in the destination ADMIN store).
- ☐ A **second movement** returned the temp asset to the **IT store** (check the IT store page).

---

## 8. Scenario H — Disposal

1. Repair & Disposal → **Disposal**: pick an asset in the IT store (or arrive here via the
   non-repairable divert of §5.4) → create → complete.

**Verify**
- ☐ Asset status → **Pending Disposal** at creation.
- ☐ On completion the asset sits in the **HO Disposal store** (Store → Disposal Store) and is
  flagged **disposed** with a disposal date — it can no longer be issued.
- ☐ Assets past their category's useful life show **"Ready for disposal"** on the Assets pages
  ("Due for Disposal" tab) independently of this flow.

---

## 9. Store-update cheat sheet (what completion does)

| Movement type | Commodity lines | Asset lines |
|---|---|---|
| ISSUANCE_FULFILLMENT (leg 1) | source ↓, dest store ↑ | relocate to dest store |
| ISSUANCE_FULFILLMENT / DEPT_TRANSFER → **user** | source ↓ only (consumed) | assign to user, leave store |
| REPLENISHMENT | source ↓, dest ↑ | relocate |
| DEPARTMENT_TRANSFER (store→store) | source ↓, dest ↑ | relocate |
| REPAIR_TRANSFER | — | relocate into IT/ADMIN store |
| TEMP_REPLACEMENT | — | **temporary** assignment to user |
| RETURN_AFTER_REPAIR | — | re-assign to user / relocate to dest store |
| DISPOSAL_TRANSFER | — | relocate to Disposal store + mark disposed |
| CANCELLED (any) | nothing | nothing |

**Golden rules to confirm while testing**
- Balances change **only at completion/receive** — never at create or dispatch.
- Re-receiving a completed movement is a no-op (`inventorySettled` applies effects exactly once).
- INTER_LOCATION (different branches) demands dispatch (courier + tracking) before receive;
  INTRA_LOCATION completes in one step.
- Each store page (Admin/IT/Disposal) shows **only its own store's** holdings — an item never
  appears in more than one store.

---

## 10. Negative tests (should all be rejected)

| # | Attempt | Expected |
|---|---|---|
| 10.1 | Dispatch without courier or tracking number | Blocked with a validation error |
| 10.2 | Receive an inter-location movement that was never dispatched | Blocked |
| 10.3 | Approve a DRAFT movement as someone other than the current approver | Blocked |
| 10.4 | External repair without a consultant / without a dispatch document | Blocked in the modal |
| 10.5 | Complete a movement whose source lacks stock | Blocked (insufficient balance) |
| 10.6 | Cancel a COMPLETED movement | Blocked |
| 10.7 | Receive the same movement twice | Second receive is a no-op (idempotent) |

---

## 11. Low-stock follow-through (closes the loop)

1. After Scenario B/C drains a branch or HO balance to ≤ its "Reorder at" threshold:
   - ☐ The balance row shows **Low** in the Consumable Balances panel; the store landing page's
     Branches Overview shows the low-stock chip against that branch (its region auto-expands).
2. From the Branches Overview, click **Replenish** → create the replenishment movement → run it
   through Scenario C again.
   - ☐ After receipt the Low flag clears.
